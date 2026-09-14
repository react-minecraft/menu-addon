package com.g4vrk.menuAddon.menu;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.defaults.DefaultActions;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.parse.time.TimeParser;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.text.serializer.impl.String2ComponentSerializer;
import net.kyori.adventure.audience.Audience;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuActionExecutor {

    private final ActionParser<Audience> actionParser;
    private final ActionParser<Player> playerActionParser;
    private final Logger logger;
    private final Navigator navigator;
    private final Set<String> reportedUnknownActions = ConcurrentHashMap.newKeySet();

    public MenuActionExecutor(
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull String2ComponentSerializer serializer,
            final @NotNull Logger logger,
            final @NotNull Navigator navigator
    ) {
        this.actionParser = actionParser;
        this.logger = logger;
        this.navigator = navigator;

        final ActionRegistry<Player> playerActionRegistry = new SimpleActionRegistry<>(true);
        DefaultActions.Player.registerDefaults(
                playerActionRegistry,
                serializer::serialize,
                ";",
                s -> TimeParser.parseDuration(s).toMillis() / 50
        );
        this.playerActionParser = new SimpleActionParser<>(playerActionRegistry);
    }

    public void execute(
            final @NotNull List<String> actions,
            final @NotNull Context context
    ) {
        for (final String configuredAction : actions) {
            final ParsedAction parsed = ParsedAction.parse(configuredAction);

            if (parsed == null) {
                continue;
            }

            if (this.executeLocal(parsed, context)) {
                continue;
            }

            final ExecutableAction<? super Player> playerAction = this.playerActionParser.parse(configuredAction);

            if (playerAction != null) {
                playerAction.execute(
                        context.viewer(),
                        value -> replace(value, context.placeholders())
                );
                continue;
            }

            final ExecutableAction<? super Audience> action = this.actionParser.parse(configuredAction);

            if (action == null) {
                if (this.reportedUnknownActions.add(parsed.name())) {
                    this.logger.warn("Unknown menu action: {}", parsed.name());
                }
                continue;
            }

            action.execute(
                    context.viewer(),
                    value -> replace(value, context.placeholders())
            );
        }
    }

    private boolean executeLocal(
            final @NotNull ParsedAction action,
            final @NotNull Context context
    ) {
        return switch (action.name()) {
            case "gm", "gamemode", "set-game-mode" -> {
                final String gameMode = replace(action.arguments(), context.placeholders()).trim();
                final GameMode resolved = switch (gameMode) {
                    case "0" -> GameMode.SURVIVAL;
                    case "1" -> GameMode.CREATIVE;
                    case "2" -> GameMode.ADVENTURE;
                    case "3" -> GameMode.SPECTATOR;
                    default -> {
                        try {
                            yield GameMode.valueOf(gameMode.toUpperCase(Locale.ROOT));
                        } catch (final IllegalArgumentException exception) {
                            yield null;
                        }
                    }
                };

                if (resolved == null) {
                    this.logger.warn("Invalid game mode in menu action: {}", gameMode);
                } else {
                    context.viewer().setGameMode(resolved);
                }

                yield true;
            }

            case "tp-to-target", "teleport-to-target" -> {
                final ReactPlayer target = context.target();

                if (target != null && target.bukkitPlayer.isOnline()) {
                    context.viewer().teleport(target.bukkitPlayer);
                }

                yield true;
            }

            case "select-clicked", "open-selected-target" -> {
                if (context.target() != null) {
                    this.navigator.openSelectedTarget(
                            context.viewer(),
                            context.target(),
                            0
                    );
                }

                yield true;
            }

            case "open-target-selector", "back" -> {
                this.navigator.openTargetSelector(context.viewer(), 0);
                yield true;
            }

            case "switch-to-page" -> {
                final String resolved = replace(action.arguments(), context.placeholders());

                try {
                    final int requestedPage = Integer.parseInt(resolved.trim()) - 1;
                    this.navigator.switchPage(context, requestedPage);
                } catch (final NumberFormatException exception) {
                    this.logger.warn("Invalid page number in menu action: {}", resolved);
                }

                yield true;
            }

            case "close-menu" -> {
                context.viewer().closeInventory();
                yield true;
            }

            default -> false;
        };
    }

    private static @NotNull String replace(
            final @NotNull String value,
            final @NotNull Map<String, String> placeholders
    ) {
        String result = value;

        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('{' + entry.getKey() + '}', entry.getValue());
        }

        return result;
    }

    public record Context(
            @NotNull Player viewer,
            @Nullable ReactPlayer target,
            @NotNull MenuHolder.Type menuType,
            int page,
            int totalPages,
            @NotNull Map<String, String> placeholders
    ) {
    }

    public interface Navigator {

        void openTargetSelector(@NotNull Player viewer, int page);

        void openSelectedTarget(@NotNull Player viewer, @NotNull ReactPlayer target, int page);

        void switchPage(@NotNull Context context, int page);
    }

    private record ParsedAction(
            @NotNull String name,
            @NotNull String arguments
    ) {

        private static @Nullable ParsedAction parse(
                final @NotNull String value
        ) {
            final String action = value.trim();

            if (action.isEmpty()) {
                return null;
            }

            int argumentStart;
            String name;

            final char first = action.charAt(0);
            final char closing = switch (first) {
                case '[' -> ']';
                case '(' -> ')';
                case '<' -> '>';
                default -> 0;
            };

            if (closing != 0) {
                final int end = action.indexOf(closing, 1);

                if (end < 0) {
                    return null;
                }

                name = action.substring(1, end).trim();
                argumentStart = end + 1;
            } else {
                final int space = action.indexOf(' ');
                final int colon = action.indexOf(':');
                final int end;

                if (space < 0) {
                    end = colon < 0 ? action.length() : colon;
                } else if (colon < 0) {
                    end = space;
                } else {
                    end = Math.min(space, colon);
                }

                name = action.substring(0, end).trim();
                argumentStart = end;
            }

            while (argumentStart < action.length()) {
                final char character = action.charAt(argumentStart);

                if (character != ' ' && character != ':') {
                    break;
                }

                argumentStart++;
            }

            return new ParsedAction(
                    name.toLowerCase(Locale.ROOT),
                    action.substring(argumentStart).trim()
            );
        }
    }
}

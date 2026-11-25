package com.g4vrk.menuAddon.menu;

import com.g4vrk.functionalActions.defaults.DefaultActionSet;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.menu.action.CompiledMenuAction;
import com.g4vrk.menuAddon.menu.action.MenuActionCompiler;
import com.g4vrk.menuAddon.menu.action.MenuActionTable;
import com.g4vrk.menuAddon.menu.action.ParsedMenuAction;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.player.registry.TrackedPlayerRegistry;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.react.api.util.TimeParser;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executes precompiled Bukkit menu actions for viewer click contexts.
 */
public final class MenuActionExecutor {

    private final MenuActionCompiler<Player> compiler;
    private final Logger logger;
    private final TaskScheduler<Player> scheduler;
    private final Navigator navigator;
    private final TrackedPlayerRegistry playerRegistry;

    public MenuActionExecutor(
            final @NotNull TrackedPlayerRegistry playerRegistry,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull TextFormatter text,
            final @NotNull Logger logger,
            final @NotNull TaskScheduler<Player> scheduler,
            final @NotNull Navigator navigator
    ) {

        this.playerRegistry = playerRegistry;
        this.logger = logger;
        this.scheduler = scheduler;
        this.navigator = navigator;

        final ActionRegistry<Player> playerActionRegistry = new SimpleActionRegistry<>(true);
        DefaultActionSet.Player.registerDefaults(
                playerActionRegistry,
                text::format,
                ";",
                s -> TimeParser.parse(s).toMillis() / 50
        );
        final ActionParser<Player> playerActionParser = new SimpleActionParser<>(playerActionRegistry);
        this.compiler = new MenuActionCompiler<>(
                actionParser,
                playerActionParser,
                name -> logger.warn("Unknown menu action: {}", name)
        );

    }

    public @NotNull MenuActionTable<Player> compileTable(
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {

        return this.compiler.compileTable(targetSelectorConfig, selectedTargetConfig);

    }

    public void execute(
            final @NotNull List<CompiledMenuAction<Player>> actions,
            final @NotNull Context context
    ) {

        for (final CompiledMenuAction<Player> compiled : actions) {
            final ParsedMenuAction local = compiled.local();
            if (local != null) {
                this.executeLocal(local, context);
                continue;
            }

            if (compiled.nativeAction() != null) {
                compiled.nativeAction().execute(
                        context.viewer(),
                        value -> MenuTextRenderer.replacePlaceholders(value, context.placeholders())
                );
                continue;
            }

            if (compiled.audienceAction() != null) {
                compiled.audienceAction().execute(
                        context.viewer(),
                        value -> MenuTextRenderer.replacePlaceholders(value, context.placeholders())
                );
            }
        }

    }

    private void executeLocal(
            final @NotNull ParsedMenuAction action,
            final @NotNull Context context
    ) {

        switch (action.name()) {
            case "gm", "gamemode", "set-game-mode" -> {
                final String gameMode = MenuTextRenderer.replacePlaceholders(
                        action.arguments(),
                        context.placeholders()
                ).trim();
                final GameMode resolved = resolveGameMode(gameMode);

                if (resolved == null) {
                    this.logger.warn("Invalid game mode in menu action: {}", gameMode);
                } else {
                    context.viewer().setGameMode(resolved);
                }
            }

            case "tp-to-target", "teleport-to-target" -> {
                final TrackedPlayer target = context.target();

                if (target != null && this.playerRegistry.get(target.uniqueId()) == target) {
                    final Player nativeTarget = (Player) target.platform().handle();
                    this.scheduler.scheduleEntity(nativeTarget, () -> {
                        if (this.playerRegistry.get(target.uniqueId()) != target) {
                            return;
                        }
                        final Location location = nativeTarget.getLocation();
                        this.scheduler.scheduleEntity(
                                context.viewer(),
                                () -> context.viewer().teleportAsync(location),
                                TickSchedule.instant()
                        );
                    }, TickSchedule.instant());
                }
            }

            case "select-clicked", "open-selected-target" -> {
                if (context.target() != null) {
                    this.navigator.openSelectedTarget(
                            context.viewer(),
                            context.target(),
                            0
                    );
                }
            }

            case "open-target-selector", "back" -> this.navigator.openTargetSelector(context.viewer(), 0);

            case "switch-to-page" -> {
                final String resolved = MenuTextRenderer.replacePlaceholders(
                        action.arguments(),
                        context.placeholders()
                );

                try {
                    final int requestedPage = Integer.parseInt(resolved.trim()) - 1;
                    this.navigator.switchPage(context, requestedPage);
                } catch (final NumberFormatException exception) {
                    this.logger.warn("Invalid page number in menu action: {}", resolved);
                }
            }

            case "close-menu" -> context.viewer().closeInventory();

            default -> {
            }
        }

    }

    private static @Nullable GameMode resolveGameMode(
            final @NotNull String value
    ) {

        return switch (value) {
            case "0" -> GameMode.SURVIVAL;
            case "1" -> GameMode.CREATIVE;
            case "2" -> GameMode.ADVENTURE;
            case "3" -> GameMode.SPECTATOR;
            default -> {
                try {
                    yield GameMode.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (final IllegalArgumentException exception) {
                    yield null;
                }
            }
        };

    }

    public record Context(
            @NotNull Player viewer,
            @Nullable TrackedPlayer target,
            @NotNull MenuType menuType,
            int page,
            int totalPages,
            @NotNull Map<String, String> placeholders
    ) {
    }

    public interface Navigator {

        void openTargetSelector(
                @NotNull Player viewer,
                int page
        );

        void openSelectedTarget(
                @NotNull Player viewer,
                @NotNull TrackedPlayer target,
                int page
        );

        void switchPage(
                @NotNull Context context,
                int page
        );

    }

}

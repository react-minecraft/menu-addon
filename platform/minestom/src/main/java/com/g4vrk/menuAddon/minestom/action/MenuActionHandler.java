package com.g4vrk.menuAddon.minestom.action;

import com.g4vrk.menuAddon.minestom.view.MenuView;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.player.TrackedPlayer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executes configured menu actions on Minestom.
 */
public final class MenuActionHandler {

    private final AddonEnvironment environment;
    private final MenuView view;

    public MenuActionHandler(
            final @NotNull AddonEnvironment environment,
            final @NotNull MenuView view
    ) {

        this.environment = environment;
        this.view = view;

    }

    public void execute(
            final @NotNull MenuTextRenderer renderer,
            final @NotNull Player viewer,
            final @NotNull List<String> actions,
            final @NotNull Map<String, String> placeholders,
            final @Nullable TrackedPlayer target,
            final int page
    ) {

        final PlatformPlayer platformPlayer = this.environment.platform().adaptPlayer(viewer);
        for (final String action : actions) {
            if (this.executePlatformAction(renderer, viewer, action, target, placeholders, page)) {
                continue;
            }
            final var parsed = this.environment.actions().parse(action);
            if (parsed != null) {
                parsed.execute(platformPlayer, value -> renderer.replace(value, placeholders));
            }
        }

    }

    private boolean executePlatformAction(
            final @NotNull MenuTextRenderer renderer,
            final @NotNull Player viewer,
            final @NotNull String configuredAction,
            final @Nullable TrackedPlayer target,
            final @NotNull Map<String, String> placeholders,
            final int page
    ) {

        final String action = configuredAction.trim();
        if (!action.startsWith("[") || action.indexOf(']') < 0) {
            return false;
        }
        final int end = action.indexOf(']');
        final String name = action.substring(1, end).trim().toLowerCase(Locale.ROOT);
        final String arguments = action.substring(end + 1).trim();
        switch (name) {
            case "gm", "gamemode", "set-game-mode" -> {
                final String mode = renderer.replace(arguments, placeholders).toUpperCase(Locale.ROOT);
                final GameMode gameMode = switch (mode) {
                    case "0", "SURVIVAL" -> GameMode.SURVIVAL;
                    case "1", "CREATIVE" -> GameMode.CREATIVE;
                    case "2", "ADVENTURE" -> GameMode.ADVENTURE;
                    case "3", "SPECTATOR" -> GameMode.SPECTATOR;
                    default -> null;
                };
                if (gameMode != null) {
                    viewer.setGameMode(gameMode);
                }
                return true;
            }
            case "tp-to-target", "teleport-to-target" -> {
                if (target != null && target.platform().online()) {
                    final Player nativeTarget = target.platform().unwrap(Player.class);
                    if (nativeTarget != null) {
                        viewer.setInstance(nativeTarget.getInstance(), nativeTarget.getPosition());
                    }
                }
                return true;
            }
            case "select-clicked", "open-selected-target" -> {
                if (target != null) {
                    this.view.openHistory(viewer, target, 0);
                }
                return true;
            }
            case "open-target-selector", "back" -> {
                this.view.openPlayers(viewer, 0);
                return true;
            }
            case "switch-to-page" -> {
                final String resolved = renderer.replace(arguments, placeholders);
                try {
                    final int requestedPage = Integer.parseInt(resolved) - 1;
                    if (target == null) {
                        this.view.openPlayers(viewer, requestedPage);
                    } else {
                        this.view.openHistory(viewer, target, requestedPage);
                    }
                } catch (final NumberFormatException ignored) {
                }
                return true;
            }
            case "close-menu" -> {
                viewer.closeInventory();
                return true;
            }
            default -> {
                return false;
            }
        }

    }

}

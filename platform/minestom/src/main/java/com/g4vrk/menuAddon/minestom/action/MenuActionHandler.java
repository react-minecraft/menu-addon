package com.g4vrk.menuAddon.minestom.action;

import com.g4vrk.menuAddon.menu.MenuType;
import com.g4vrk.menuAddon.menu.action.CompiledMenuAction;
import com.g4vrk.menuAddon.menu.action.ParsedMenuAction;
import com.g4vrk.menuAddon.minestom.view.MenuView;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.player.TrackedPlayer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executes precompiled menu actions on Minestom.
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
            final @NotNull Player viewer,
            final @NotNull List<CompiledMenuAction<Void>> actions,
            final @NotNull Map<String, String> placeholders,
            final @Nullable TrackedPlayer target,
            final @NotNull MenuType menuType
    ) {

        if (actions.isEmpty()) {
            return;
        }

        final PlatformPlayer platformPlayer = this.environment.platform().adaptPlayer(viewer);
        for (final CompiledMenuAction<Void> compiled : actions) {
            final ParsedMenuAction local = compiled.local();
            if (local != null) {
                this.executePlatformAction(viewer, local, target, placeholders, menuType);
                continue;
            }
            if (compiled.audienceAction() != null) {
                compiled.audienceAction().execute(
                        platformPlayer,
                        value -> MenuTextRenderer.replacePlaceholders(value, placeholders)
                );
            }
        }

    }

    private void executePlatformAction(
            final @NotNull Player viewer,
            final @NotNull ParsedMenuAction action,
            final @Nullable TrackedPlayer target,
            final @NotNull Map<String, String> placeholders,
            final @NotNull MenuType menuType
    ) {

        switch (action.name()) {
            case "gm", "gamemode", "set-game-mode" -> {
                final String mode = MenuTextRenderer.replacePlaceholders(action.arguments(), placeholders)
                        .trim()
                        .toUpperCase(Locale.ROOT);
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
            }
            case "tp-to-target", "teleport-to-target" -> {
                if (target != null && target.platform().online()) {
                    final Player nativeTarget = target.platform().unwrap(Player.class);
                    if (nativeTarget != null) {
                        final Instance targetInstance = nativeTarget.getInstance();
                        if (targetInstance != null) {
                            if (viewer.getInstance() == targetInstance) {
                                viewer.teleport(nativeTarget.getPosition());
                            } else {
                                viewer.setInstance(targetInstance, nativeTarget.getPosition());
                            }
                        }
                    }
                }
            }
            case "select-clicked", "open-selected-target" -> {
                if (target != null) {
                    this.view.openHistory(viewer, target, 0);
                }
            }
            case "open-target-selector", "back" -> this.view.openPlayers(viewer, 0);
            case "switch-to-page" -> {
                final String resolved = MenuTextRenderer.replacePlaceholders(action.arguments(), placeholders);
                try {
                    final int requestedPage = Integer.parseInt(resolved.trim()) - 1;
                    if (menuType == MenuType.TARGET_SELECTOR) {
                        this.view.openPlayers(viewer, requestedPage);
                    } else if (target != null) {
                        this.view.openHistory(viewer, target, requestedPage);
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
            case "close-menu" -> viewer.closeInventory();
            default -> {
            }
        }

    }

}

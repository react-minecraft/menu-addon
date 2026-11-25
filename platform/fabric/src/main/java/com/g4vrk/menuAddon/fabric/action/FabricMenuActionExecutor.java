package com.g4vrk.menuAddon.fabric.action;

import com.g4vrk.menuAddon.menu.MenuType;
import com.g4vrk.menuAddon.menu.action.CompiledMenuAction;
import com.g4vrk.menuAddon.menu.action.ParsedMenuAction;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.player.TrackedPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Executes precompiled menu actions and native player actions on Fabric.
 */
public final class FabricMenuActionExecutor {

    private final AddonEnvironment environment;
    private final BiConsumer<ServerPlayer, Integer> openPlayers;
    private final HistoryOpener openHistory;

    public FabricMenuActionExecutor(
            final @NotNull AddonEnvironment environment,
            final @NotNull BiConsumer<ServerPlayer, Integer> openPlayers,
            final @NotNull HistoryOpener openHistory
    ) {

        this.environment = environment;
        this.openPlayers = openPlayers;
        this.openHistory = openHistory;

    }

    public void execute(
            final @NotNull ServerPlayer viewer,
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
            final @NotNull ServerPlayer viewer,
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
                final GameType gameType = switch (mode) {
                    case "0", "SURVIVAL" -> GameType.SURVIVAL;
                    case "1", "CREATIVE" -> GameType.CREATIVE;
                    case "2", "ADVENTURE" -> GameType.ADVENTURE;
                    case "3", "SPECTATOR" -> GameType.SPECTATOR;
                    default -> null;
                };
                if (gameType != null) {
                    viewer.setGameMode(gameType);
                }
            }
            case "tp-to-target", "teleport-to-target" -> {
                if (target != null && target.platform().online()) {
                    final ServerPlayer nativeTarget = target.platform().unwrap(ServerPlayer.class);
                    if (nativeTarget != null && !nativeTarget.isRemoved()) {
                        viewer.teleportTo(
                                nativeTarget.level(),
                                nativeTarget.getX(),
                                nativeTarget.getY(),
                                nativeTarget.getZ(),
                                Set.of(),
                                nativeTarget.getYRot(),
                                nativeTarget.getXRot(),
                                true
                        );
                    }
                }
            }
            case "select-clicked", "open-selected-target" -> {
                if (target != null) {
                    this.openHistory.open(viewer, target, 0);
                }
            }
            case "open-target-selector", "back" -> this.openPlayers.accept(viewer, 0);
            case "switch-to-page" -> {
                final String resolved = MenuTextRenderer.replacePlaceholders(action.arguments(), placeholders);
                try {
                    final int requestedPage = Integer.parseInt(resolved.trim()) - 1;
                    if (menuType == MenuType.TARGET_SELECTOR) {
                        this.openPlayers.accept(viewer, requestedPage);
                    } else if (target != null) {
                        this.openHistory.open(viewer, target, requestedPage);
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
            case "close-menu" -> viewer.closeContainer();
            default -> {
            }
        }

    }

    /**
     * Opens a selected player's history at a page.
     */
    @FunctionalInterface
    public interface HistoryOpener {

        void open(
                @NotNull ServerPlayer viewer,
                @NotNull TrackedPlayer target,
                int page
        );

    }

}

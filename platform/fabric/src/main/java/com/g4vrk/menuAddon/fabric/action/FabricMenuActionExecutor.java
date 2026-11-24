package com.g4vrk.menuAddon.fabric.action;

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
import java.util.function.Supplier;

/**
 * Executes configured actions and native player actions for menu clicks.
 */
public final class FabricMenuActionExecutor {

    private final AddonEnvironment environment;
    private final Supplier<MenuTextRenderer> renderer;
    private final BiConsumer<ServerPlayer, Integer> openPlayers;
    private final HistoryOpener openHistory;

    public FabricMenuActionExecutor(
            final @NotNull AddonEnvironment environment,
            final @NotNull Supplier<MenuTextRenderer> renderer,
            final @NotNull BiConsumer<ServerPlayer, Integer> openPlayers,
            final @NotNull HistoryOpener openHistory
    ) {

        this.environment = environment;
        this.renderer = renderer;
        this.openPlayers = openPlayers;
        this.openHistory = openHistory;

    }

    public void execute(
            final @NotNull ServerPlayer viewer,
            final @NotNull List<String> actions,
            final @NotNull Map<String, String> placeholders,
            final @Nullable TrackedPlayer target,
            final int page
    ) {

        final PlatformPlayer platformPlayer = this.environment.platform().adaptPlayer(viewer);
        for (final String action : actions) {
            if (this.executePlatformAction(viewer, action, target, placeholders, page)) {
                continue;
            }
            final var parsed = this.environment.actions().parse(action);
            if (parsed != null) {
                parsed.execute(platformPlayer, value -> this.renderer.get().replace(value, placeholders));
            }
        }

    }

    private boolean executePlatformAction(
            final @NotNull ServerPlayer viewer,
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
                final String mode = this.renderer.get().replace(arguments, placeholders).toUpperCase(Locale.ROOT);
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
                return true;
            }
            case "tp-to-target", "teleport-to-target" -> {
                if (target != null && target.platform().online()) {
                    final ServerPlayer nativeTarget = target.platform().unwrap(ServerPlayer.class);
                    if (nativeTarget != null) {
                        viewer.teleportTo(nativeTarget.level(), nativeTarget.getX(), nativeTarget.getY(),
                                nativeTarget.getZ(), Set.of(), nativeTarget.getYRot(),
                                nativeTarget.getXRot(), true);
                    }
                }
                return true;
            }
            case "select-clicked", "open-selected-target" -> {
                if (target != null) {
                    this.openHistory.open(viewer, target, 0);
                }
                return true;
            }
            case "open-target-selector", "back" -> {
                this.openPlayers.accept(viewer, 0);
                return true;
            }
            case "switch-to-page" -> {
                final String resolved = this.renderer.get().replace(arguments, placeholders);
                try {
                    final int requestedPage = Integer.parseInt(resolved) - 1;
                    if (target == null) {
                        this.openPlayers.accept(viewer, requestedPage);
                    } else {
                        this.openHistory.open(viewer, target, requestedPage);
                    }
                } catch (final NumberFormatException ignored) {
                }
                return true;
            }
            case "close-menu" -> {
                viewer.closeContainer();
                return true;
            }
            default -> {
                return false;
            }
        }

    }

    /**
     * Opens a selected player's history at a page.
     */
    @FunctionalInterface
    public interface HistoryOpener {

        void open(@NotNull ServerPlayer viewer, @NotNull TrackedPlayer target, int page);

    }

}

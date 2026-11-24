package com.g4vrk.menuAddon.sponge.action;

import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.player.TrackedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.entity.living.player.gamemode.GameMode;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.world.server.ServerLocation;

import java.util.Locale;
import java.util.Map;

/**
 * Executes menu actions that operate on Sponge players.
 */
public final class SpongePlayerAction {

    private SpongePlayerAction() {

    }

    public static boolean execute(
            final @NotNull ServerPlayer viewer,
            final @NotNull String name,
            final @NotNull String arguments,
            final @Nullable TrackedPlayer target,
            final @NotNull Map<String, String> placeholders,
            final @NotNull MenuTextRenderer renderer
    ) {

        switch (name) {
            case "gm", "gamemode", "set-game-mode" -> {
                final String mode = renderer.replace(arguments, placeholders).toUpperCase(Locale.ROOT);
                final GameMode gameMode = switch (mode) {
                    case "0", "SURVIVAL" -> GameModes.SURVIVAL.get();
                    case "1", "CREATIVE" -> GameModes.CREATIVE.get();
                    case "2", "ADVENTURE" -> GameModes.ADVENTURE.get();
                    case "3", "SPECTATOR" -> GameModes.SPECTATOR.get();
                    default -> null;
                };
                if (gameMode != null) {
                    viewer.gameMode().set(gameMode);
                }
                return true;
            }
            case "tp-to-target", "teleport-to-target" -> {
                if (target != null && target.platform().online()) {
                    final ServerPlayer nativeTarget = target.platform().unwrap(ServerPlayer.class);
                    if (nativeTarget != null) {
                        viewer.setLocationAndRotation(ServerLocation.of(nativeTarget.world(), nativeTarget.position()),
                                nativeTarget.rotation());
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }

    }

}

package com.g4vrk.menuAddon.fabric.session;

import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.player.TrackedPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Owns native menu sessions and their click lifecycle.
 */
public final class FabricMenuSessionManager {

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final AddonEnvironment environment;
    private final Supplier<String> permission;

    public FabricMenuSessionManager(
            final @NotNull AddonEnvironment environment,
            final @NotNull Supplier<String> permission
    ) {

        this.environment = environment;
        this.permission = permission;

    }

    public @NotNull List<Session> snapshot() {

        return List.copyOf(this.sessions.values());

    }

    public void closeAll() {

        for (final Session session : this.snapshot()) {
            session.player().closeContainer();
        }
        this.sessions.clear();

    }

    public void open(
            final @NotNull ServerPlayer viewer,
            final @NotNull Component title,
            final @NotNull SimpleContainer container,
            final int page,
            final @NotNull List<TrackedPlayer> players,
            final @Nullable TrackedPlayer historyTarget,
            final @NotNull BiConsumer<Integer, Boolean> action
    ) {

        final MenuProvider provider = new SimpleMenuProvider((syncId, inventory, nativePlayer) -> {
            final int rows = container.getContainerSize() / 9;
            final ChestMenu menu = new ChestMenu(menuType(rows), syncId, inventory, container, rows) {

                @Override
                public void clicked(
                        final int slot,
                        final int button,
                        final ContainerInput click,
                        final Player player
                ) {

                    if (player == viewer && slot >= 0 && slot < container.getContainerSize()
                            && (button == 0 || button == 1)) {
                        final PlatformPlayer platformPlayer = environment.platform().adaptPlayer(viewer);
                        if (!permission.get().isEmpty() && !platformPlayer.hasPermission(permission.get())) {
                            viewer.closeContainer();
                            return;
                        }
                        action.accept(slot, button == 0);
                    }

                }

                @Override
                public ItemStack quickMoveStack(final Player player, final int slot) {

                    return ItemStack.EMPTY;

                }

                @Override
                public void removed(final Player player) {

                    super.removed(player);
                    final Session session = sessions.get(viewer.getUUID());
                    if (session != null && session.menu() == this) {
                        sessions.remove(viewer.getUUID());
                    }

                }

            };
            this.sessions.put(viewer.getUUID(), new Session(viewer, menu, page, List.copyOf(players), historyTarget));
            return menu;
        }, title);
        viewer.openMenu(provider);

    }

    private static @NotNull MenuType<?> menuType(final int rows) {

        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };

    }

    /**
     * Captures the menu and page currently open for a player.
     */
    public record Session(
            @NotNull ServerPlayer player,
            @NotNull ChestMenu menu,
            int page,
            @NotNull List<TrackedPlayer> players,
            @Nullable TrackedPlayer historyTarget
    ) {
    }

}

package com.g4vrk.menuAddon.fabric.session;

import com.g4vrk.menuAddon.menu.MenuType;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Owns native Fabric menu sessions and their click lifecycle.
 */
public final class FabricMenuSessionManager {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
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

    public @Nullable Session current(
            final @NotNull UUID playerId
    ) {

        return this.sessions.get(playerId);

    }

    public boolean isCurrent(
            final @NotNull Session session
    ) {

        final ServerPlayer player = session.player();
        return this.sessions.get(player.getUUID()) == session
                && !player.hasDisconnected()
                && player.containerMenu == session.menu();

    }

    public void replace(
            final @NotNull Session expected,
            final @NotNull Session updated
    ) {

        this.sessions.replace(expected.player().getUUID(), expected, updated);

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
            final @NotNull MenuType menuType,
            final int page,
            final int totalPages,
            final @NotNull List<TrackedPlayer> players,
            final @Nullable TrackedPlayer historyTarget,
            final @NotNull ClickAction action
    ) {

        final Session[] created = new Session[1];
        final MenuProvider provider = new SimpleMenuProvider((syncId, inventory, nativePlayer) -> {
            final int rows = container.getContainerSize() / 9;
            final ChestMenu menu = new ChestMenu(minecraftMenuType(rows), syncId, inventory, container, rows) {

                @Override
                public void clicked(
                        final int slot,
                        final int button,
                        final ContainerInput click,
                        final Player player
                ) {

                    if (player == viewer && slot >= 0 && slot < container.getContainerSize()
                            && (button == 0 || button == 1)) {
                        final Session active = FabricMenuSessionManager.this.sessions.get(viewer.getUUID());
                        if (active == null || active.menu() != this) {
                            return;
                        }
                        final PlatformPlayer platformPlayer =
                                FabricMenuSessionManager.this.environment.platform().adaptPlayer(viewer);
                        final String required = FabricMenuSessionManager.this.permission.get();
                        if (!required.isEmpty() && !platformPlayer.hasPermission(required)) {
                            viewer.closeContainer();
                            return;
                        }
                        action.onClick(active, slot, button == 0);
                    }

                }

                @Override
                public ItemStack quickMoveStack(
                        final Player player,
                        final int slot
                ) {

                    return ItemStack.EMPTY;

                }

                @Override
                public void removed(
                        final Player player
                ) {

                    super.removed(player);
                    final Session session = FabricMenuSessionManager.this.sessions.get(viewer.getUUID());
                    if (session != null && session.menu() == this) {
                        FabricMenuSessionManager.this.sessions.remove(viewer.getUUID(), session);
                    }

                }

            };
            final Session session = new Session(
                    viewer,
                    menu,
                    container,
                    menuType,
                    page,
                    totalPages,
                    List.copyOf(players),
                    historyTarget
            );
            created[0] = session;
            this.sessions.put(viewer.getUUID(), session);
            return menu;
        }, title);
        viewer.openMenu(provider);
        if (created[0] != null && viewer.containerMenu != created[0].menu()) {
            this.sessions.remove(viewer.getUUID(), created[0]);
        }

    }

    private static @NotNull net.minecraft.world.inventory.MenuType<?> minecraftMenuType(
            final int rows
    ) {

        return switch (rows) {
            case 1 -> net.minecraft.world.inventory.MenuType.GENERIC_9x1;
            case 2 -> net.minecraft.world.inventory.MenuType.GENERIC_9x2;
            case 3 -> net.minecraft.world.inventory.MenuType.GENERIC_9x3;
            case 4 -> net.minecraft.world.inventory.MenuType.GENERIC_9x4;
            case 5 -> net.minecraft.world.inventory.MenuType.GENERIC_9x5;
            default -> net.minecraft.world.inventory.MenuType.GENERIC_9x6;
        };

    }

    /**
     * Handles clicks against the latest active menu session.
     */
    @FunctionalInterface
    public interface ClickAction {

        void onClick(
                @NotNull Session session,
                int slot,
                boolean leftClick
        );

    }

    /**
     * Captures the menu and page currently open for a player.
     */
    public record Session(
            @NotNull ServerPlayer player,
            @NotNull ChestMenu menu,
            @NotNull SimpleContainer container,
            @NotNull MenuType menuType,
            int page,
            int totalPages,
            @NotNull List<TrackedPlayer> players,
            @Nullable TrackedPlayer historyTarget
    ) {
    }

}

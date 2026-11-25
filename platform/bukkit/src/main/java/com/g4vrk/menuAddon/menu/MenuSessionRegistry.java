package com.g4vrk.menuAddon.menu;

import com.g4vrk.menuAddon.util.InventoryViewCompat;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

/**
 * Tracks open Bukkit menu holders and coordinates async and synchronous shutdown closing.
 */
public final class MenuSessionRegistry {

    private final TaskScheduler<Player> scheduler;
    private final Object viewersLock = new Object();
    private final Map<UUID, MenuHolder> openMenus = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingClose = new HashMap<>();
    private final Set<UUID> closingViewers = new HashSet<>();
    private boolean stopping;
    private long closeGeneration;

    public MenuSessionRegistry(
            final @NotNull TaskScheduler<Player> scheduler
    ) {

        this.scheduler = scheduler;

    }

    public @NotNull List<Player> openViewers() {

        final List<UUID> viewerIds;
        synchronized (this.viewersLock) {
            if (this.openMenus.isEmpty()) {
                return List.of();
            }
            viewerIds = new ArrayList<>(this.openMenus.keySet());
        }

        final List<Player> viewers = new ArrayList<>(viewerIds.size());
        for (final UUID viewerId : viewerIds) {
            final Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                viewers.add(viewer);
            }
        }
        return viewers;

    }

    public void openInventory(
            final @NotNull Player viewer,
            final @NotNull MenuHolder holder,
            final @NotNull Inventory inventory,
            final @NotNull BiPredicate<Player, MenuHolder> refreshTick
    ) {

        synchronized (this.viewersLock) {
            if (this.stopping) {
                return;
            }
            viewer.openInventory(inventory);
            if (InventoryViewCompat.getTopInventory(viewer) == inventory) {
                this.openMenus.put(viewer.getUniqueId(), holder);
                this.startRefresh(viewer, holder, refreshTick);
            }
        }

    }

    public @NotNull CompletableFuture<Void> closeAll() {

        synchronized (this.viewersLock) {
            this.stopping = true;
            final long generation = ++this.closeGeneration;

            for (final Map.Entry<UUID, MenuHolder> entry : new ArrayList<>(this.openMenus.entrySet())) {
                final UUID playerId = entry.getKey();
                final MenuHolder expected = entry.getValue();
                final Player player = Bukkit.getPlayer(playerId);

                if (player == null || !player.isOnline()) {
                    expected.stopRefresh();
                    expected.getInventory().clear();
                    this.openMenus.remove(playerId, expected);
                    this.completePending(playerId);
                    continue;
                }

                final CompletableFuture<Void> closed = this.pendingClose.computeIfAbsent(
                        playerId,
                        ignored -> new CompletableFuture<>()
                );

                try {
                    this.scheduler.scheduleEntity(player, () -> {
                        try {
                            synchronized (this.viewersLock) {
                                if (!this.stopping || this.closeGeneration != generation) {
                                    return;
                                }
                                this.closingViewers.add(playerId);
                                try {
                                    if (player.isOnline()
                                            && InventoryViewCompat.getTopInventory(player).getHolder() == expected) {
                                        player.closeInventory();
                                    }
                                    expected.stopRefresh();
                                    expected.getInventory().clear();
                                    this.menuClosed(player, expected);
                                } finally {
                                    this.closingViewers.remove(playerId);
                                }
                                this.viewerClosed(playerId);
                            }
                        } catch (final RuntimeException exception) {
                            closed.completeExceptionally(exception);
                        }
                    }, TickSchedule.instant());
                } catch (final RuntimeException exception) {
                    closed.completeExceptionally(exception);
                }
            }

            for (final UUID pendingId : new ArrayList<>(this.pendingClose.keySet())) {
                if (!this.openMenus.containsKey(pendingId)) {
                    this.completePending(pendingId);
                }
            }

            final CompletableFuture<Void> result = CompletableFuture.allOf(
                    this.pendingClose.values().toArray(CompletableFuture[]::new)
            );
            result.whenComplete((ignored, failure) -> {
                if (failure != null) {
                    synchronized (this.viewersLock) {
                        this.pendingClose.clear();
                        this.stopping = false;
                    }
                }
            });
            return result;
        }

    }

    public void closeImmediately() {

        synchronized (this.viewersLock) {
            this.stopping = true;
            this.closeGeneration++;

            for (final Map.Entry<UUID, MenuHolder> entry : new ArrayList<>(this.openMenus.entrySet())) {
                final MenuHolder holder = entry.getValue();
                holder.stopRefresh();
                holder.getInventory().clear();

                final Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    try {
                        if (InventoryViewCompat.getTopInventory(player).getHolder() == holder) {
                            player.closeInventory();
                        }
                    } catch (final RuntimeException ignored) {
                    }
                }
            }

            this.openMenus.clear();
            for (final CompletableFuture<Void> closed : this.pendingClose.values()) {
                closed.complete(null);
            }
            this.pendingClose.clear();
        }

    }

    public void cancelClose() {

        synchronized (this.viewersLock) {
            this.pendingClose.clear();
            this.stopping = false;
            this.closeGeneration++;
        }

    }

    public void menuClosed(
            final @NotNull Player viewer,
            final @NotNull MenuHolder holder
    ) {

        synchronized (this.viewersLock) {
            if (this.openMenus.remove(viewer.getUniqueId(), holder)) {
                this.viewerClosed(viewer.getUniqueId());
            }
        }

    }

    public void viewerClosed(
            final @NotNull UUID playerId
    ) {

        synchronized (this.viewersLock) {
            this.openMenus.remove(playerId);
            if (this.closingViewers.contains(playerId)) {
                return;
            }
            this.completePending(playerId);
        }

    }

    private void completePending(
            final @NotNull UUID playerId
    ) {

        final CompletableFuture<Void> closed = this.pendingClose.remove(playerId);
        if (closed != null) {
            closed.complete(null);
        }

    }

    private void startRefresh(
            final @NotNull Player viewer,
            final @NotNull MenuHolder holder,
            final @NotNull BiPredicate<Player, MenuHolder> refreshTick
    ) {

        holder.refreshTask(this.scheduler.scheduleEntity(viewer, task -> {
            if (InventoryViewCompat.getTopInventory(viewer).getHolder() != holder) {
                task.cancel();
                return;
            }
            if (!refreshTick.test(viewer, holder)) {
                task.cancel();
                return;
            }
            holder.updateTime();
        }, TickSchedule.repeating(20, 20)));

    }

}

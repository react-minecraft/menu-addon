package com.g4vrk.menuAddon.listener;

import com.g4vrk.menuAddon.menu.MenuHolder;
import com.g4vrk.menuAddon.util.InventoryViewCompat;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class MenuListener implements Listener {

    private final Scheduler scheduler;

    public MenuListener(final @NotNull Scheduler scheduler) {

        this.scheduler = scheduler;

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(
            final @NotNull InventoryClickEvent event
    ) {
        final Inventory topInventory =
                InventoryViewCompat.getTopInventory(event.getView());

        if (!(topInventory.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();

        if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
            final MenuHolder.ClickHandler handler = holder.clickHandler(rawSlot);
            if (handler == null) {
                return;
            }
            final var click = event.getClick();
            this.scheduler.scheduleEntity(player, () -> {
                if (InventoryViewCompat.getTopInventory(player).getHolder() == holder) {
                    handler.handle(player, click);
                }
            }, TickSchedule.instant());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(
            final @NotNull InventoryDragEvent event
    ) {
        final Inventory topInventory =
                InventoryViewCompat.getTopInventory(event.getView());

        if (!(topInventory.getHolder() instanceof MenuHolder)) {
            return;
        }

        if (event.getRawSlots().stream()
                .anyMatch(slot -> slot < topInventory.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final @NotNull InventoryCloseEvent event) {

        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            holder.stopRefresh();
        }

    }
}

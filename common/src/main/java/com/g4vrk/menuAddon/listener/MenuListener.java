package com.g4vrk.menuAddon.listener;

import com.g4vrk.menuAddon.menu.MenuHolder;
import com.g4vrk.menuAddon.util.InventoryViewCompat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class MenuListener implements Listener {

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
            holder.click(player, rawSlot, event.getClick());
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
}
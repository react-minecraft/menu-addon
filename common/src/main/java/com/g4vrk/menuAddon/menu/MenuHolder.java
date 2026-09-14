package com.g4vrk.menuAddon.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuHolder implements InventoryHolder {

    private final Type type;
    private final UUID targetId;
    private final Map<Integer, ClickHandler> clickHandlers = new HashMap<>();

    private Inventory inventory;
    private int page;
    private int totalPages;

    public MenuHolder(
            final @NotNull Type type,
            final int page,
            final int totalPages,
            final @Nullable UUID targetId
    ) {
        this.type = type;
        this.page = page;
        this.totalPages = totalPages;
        this.targetId = targetId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (this.inventory == null) {
            throw new IllegalStateException("Menu inventory has not been attached yet");
        }

        return this.inventory;
    }

    public void attach(
            final @NotNull Inventory inventory
    ) {
        if (this.inventory != null) {
            throw new IllegalStateException("Menu inventory is already attached");
        }

        this.inventory = inventory;
    }

    public void reset(
            final int page,
            final int totalPages
    ) {
        this.page = page;
        this.totalPages = totalPages;
        this.clickHandlers.clear();
        this.getInventory().clear();
    }

    public void bind(
            final int slot,
            final @NotNull ClickHandler clickHandler
    ) {
        this.clickHandlers.put(slot, clickHandler);
    }

    public void click(
            final @NotNull Player player,
            final int slot,
            final @NotNull ClickType clickType
    ) {
        final ClickHandler clickHandler = this.clickHandlers.get(slot);

        if (clickHandler != null) {
            clickHandler.handle(player, clickType);
        }
    }

    public @NotNull Type type() {
        return this.type;
    }

    public int page() {
        return this.page;
    }

    public int totalPages() {
        return this.totalPages;
    }

    public @Nullable UUID targetId() {
        return this.targetId;
    }

    public enum Type {
        TARGET_SELECTOR,
        SELECTED_TARGET
    }

    @FunctionalInterface
    public interface ClickHandler {
        void handle(@NotNull Player player, @NotNull ClickType clickType);
    }
}

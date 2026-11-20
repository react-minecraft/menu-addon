package com.g4vrk.menuAddon.menu;

import com.g4vrk.schedula.task.Task;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MenuHolder implements InventoryHolder {

    private final Type type;
    private final UUID targetId;
    private final Map<Integer, ClickHandler> clickHandlers = new HashMap<>();
    private final List<Runnable> timeUpdates = new ArrayList<>();

    private Inventory inventory;
    private @Nullable Task refreshTask;
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
        this.timeUpdates.clear();
        this.getInventory().clear();

    }

    public void bind(
            final int slot,
            final @NotNull ClickHandler clickHandler
    ) {

        this.clickHandlers.put(slot, clickHandler);

    }

    public @Nullable ClickHandler clickHandler(final int slot) {

        return this.clickHandlers.get(slot);

    }

    public void addTimeUpdate(final @NotNull Runnable update) {

        this.timeUpdates.add(update);

    }

    public void updateTime() {

        for (final Runnable update : this.timeUpdates) {
            update.run();
        }

    }

    public void refreshTask(final @NotNull Task task) {

        this.refreshTask = task;

    }

    public void stopRefresh() {

        if (this.refreshTask != null) {
            this.refreshTask.cancel();
            this.refreshTask = null;
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

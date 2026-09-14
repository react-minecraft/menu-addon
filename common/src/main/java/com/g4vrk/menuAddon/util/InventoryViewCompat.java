package com.g4vrk.menuAddon.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class InventoryViewCompat {

    private static final Method GET_TOP_INVENTORY = resolveTopInventoryMethod();

    private InventoryViewCompat() {
    }

    public static @NotNull Inventory getTopInventory(
            final @NotNull Player player
    ) {
        return getTopInventory((Object) player.getOpenInventory());
    }

    public static @NotNull Inventory getTopInventory(
            final @NotNull Object view
    ) {
        try {
            return (Inventory) GET_TOP_INVENTORY.invoke(view);
        } catch (final IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Cannot access InventoryView#getTopInventory",
                    exception
            );
        } catch (final InvocationTargetException exception) {
            throw new IllegalStateException(
                    "InventoryView#getTopInventory threw an exception",
                    exception.getCause()
            );
        }
    }

    private static @NotNull Method resolveTopInventoryMethod() {
        try {

            final Class<?> inventoryView = Class.forName(
                    "org.bukkit.inventory.InventoryView"
            );

            return inventoryView.getMethod("getTopInventory");

        } catch (final ReflectiveOperationException exception) {

            throw new ExceptionInInitializerError(exception);

        }
    }
}
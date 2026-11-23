package com.g4vrk.menuAddon.menu.render;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Platform-independent menu item content before it is rendered by a server GUI.
 */
public record MenuItemData(
        @NotNull String material,
        @Nullable String color,
        @NotNull Component name,
        @NotNull List<Component> lore,
        @Nullable UUID owner
) {

    public MenuItemData {

        lore = List.copyOf(lore);

    }

}

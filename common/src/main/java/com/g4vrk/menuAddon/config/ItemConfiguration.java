package com.g4vrk.menuAddon.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;

public record ItemConfiguration(
        @NotNull String material,
        @Nullable String color,
        @NotNull String name,
        @NotNull List<String> lore,
        @NotNull List<Integer> slots,
        @NotNull List<String> leftClickActions,
        @NotNull List<String> rightClickActions
) {

    static @NotNull ItemConfiguration read(
            final @NotNull ConfigurationNode node,
            final int inventorySize
    ) {

        return new ItemConfiguration(
                ConfigurationUtil.material(node.node("material"), "minecraft:stone"),
                ConfigurationUtil.color(node.node("color")),
                node.node("name").getString("&r"),
                ConfigurationUtil.strings(node.node("lore")),
                ConfigurationUtil.slots(node.node("slots"), inventorySize),
                ConfigurationUtil.strings(node.node("on-left-click")),
                ConfigurationUtil.strings(node.node("on-right-click"))
        );

    }

}

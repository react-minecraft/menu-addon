package com.g4vrk.menuAddon.config;

import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ConfigurationUtil {

    private ConfigurationUtil() {
    }

    static @NotNull List<String> strings(
            final @NotNull ConfigurationNode node
    ) {
        final List<String> result = new ArrayList<>();

        for (final ConfigurationNode child : node.childrenList()) {
            final String value = child.getString();

            if (value != null) {
                result.add(value);
            }
        }

        return List.copyOf(result);
    }

    static @NotNull List<Integer> slots(
            final @NotNull ConfigurationNode node,
            final int inventorySize
    ) {
        final Set<Integer> result = new LinkedHashSet<>();

        for (final ConfigurationNode child : node.childrenList()) {
            final String expression = child.getString();

            if (expression == null || expression.isBlank()) {
                continue;
            }

            final String value = expression.trim();
            final int separator = value.indexOf('-');

            if (separator < 0) {
                addSlot(result, parseInteger(value, "slot"), inventorySize);
                continue;
            }

            final int start = parseInteger(value.substring(0, separator).trim(), "slot range start");
            final int end = parseInteger(value.substring(separator + 1).trim(), "slot range end");

            if (start > end) {
                throw new IllegalArgumentException("Invalid descending slot range: " + value);
            }

            for (int slot = start; slot <= end; slot++) {
                addSlot(result, slot, inventorySize);
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("At least one inventory slot must be configured");
        }

        return List.copyOf(result);
    }

    static int inventorySize(
            final @NotNull ConfigurationNode node
    ) {
        final int size = node.getInt(54);

        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException(
                    "Inventory size must be a multiple of 9 between 9 and 54, got " + size
            );
        }

        return size;
    }

    static @NotNull Material material(
            final @NotNull ConfigurationNode node,
            final @NotNull Material fallback
    ) {
        final String configured = node.getString();

        if (configured == null || configured.isBlank()) {
            return fallback;
        }

        final Material material = Material.matchMaterial(configured.trim().toUpperCase(Locale.ROOT));

        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Unknown item material: " + configured);
        }

        return material;
    }

    static @Nullable Color color(
            final @NotNull ConfigurationNode node
    ) {
        final String value = node.getString();

        if (value == null || value.isBlank()) {
            return null;
        }

        final String hex = value.startsWith("#") ? value.substring(1) : value;

        if (!hex.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Invalid RGB color: " + value);
        }

        return Color.fromRGB(Integer.parseInt(hex, 16));
    }

    static @NotNull String decimalFormat(
            final @NotNull ConfigurationNode node,
            final @NotNull String fallback
    ) {
        final String pattern = node.getString(fallback);

        try {
            new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid decimal format: " + pattern, exception);
        }

        return pattern;
    }

    private static int parseInteger(
            final @NotNull String value,
            final @NotNull String description
    ) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + description + ": " + value, exception);
        }
    }

    private static void addSlot(
            final @NotNull Set<Integer> slots,
            final int slot,
            final int inventorySize
    ) {
        if (slot < 0 || slot >= inventorySize) {
            throw new IllegalArgumentException(
                    "Inventory slot " + slot + " is outside configured size " + inventorySize
            );
        }

        slots.add(slot);
    }
}

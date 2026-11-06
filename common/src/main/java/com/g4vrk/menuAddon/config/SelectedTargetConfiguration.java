package com.g4vrk.menuAddon.config;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SelectedTargetConfiguration {

    private final String title;
    private final int size;
    private final List<Integer> allowedSlots;
    private final EntryItem entryItem;
    private final Map<String, ItemConfiguration> items;

    public SelectedTargetConfiguration(
            final @NotNull ConfigurationNode node
    ) {
        this.title = node.node("title").getString("&8Аналитика {player.name}");
        this.size = ConfigurationUtil.inventorySize(node.node("size"));
        this.allowedSlots = ConfigurationUtil.slots(
                node.node("listing", "allowed-slots"),
                this.size
        );
        this.entryItem = EntryItem.read(node.node("entry-template"));
        this.items = readItems(node.node("items"), this.size);
    }

    public @NotNull String title() {
        return this.title;
    }

    public int size() {
        return this.size;
    }

    public @NotNull List<Integer> allowedSlots() {
        return this.allowedSlots;
    }

    public @NotNull EntryItem entryItem() {
        return this.entryItem;
    }

    public @NotNull Map<String, ItemConfiguration> items() {
        return this.items;
    }

    private static @NotNull Map<String, ItemConfiguration> readItems(
            final @NotNull ConfigurationNode node,
            final int inventorySize
    ) {
        final Map<String, ItemConfiguration> result = new LinkedHashMap<>();

        node.childrenMap().forEach((key, child) -> result.put(
                String.valueOf(key),
                ItemConfiguration.read(child, inventorySize)
        ));

        return Map.copyOf(result);
    }

    public record EntryItem(
            int failuresPerEntry,
            @NotNull String decimalFormat,
            @NotNull String failureFormat,
            @NotNull ColoredPane coloredPane,
            @NotNull Material material,
            @NotNull String name,
            @NotNull List<String> loreHeader,
            @NotNull List<String> loreFooter,
            boolean updateOnFailure,
            @NotNull List<String> leftClickActions,
            @NotNull List<String> rightClickActions
    ) {

        private static @NotNull EntryItem read(
                final @NotNull ConfigurationNode node
        ) {
            return new EntryItem(
                    Math.max(1, node.node("failures", "per-entry").getInt(11)),
                    ConfigurationUtil.decimalFormat(
                            node.node("failures", "decimal-format"),
                            "0.00000"
                    ),
                    node.node("failures", "format").getString(
                            "{probability:colored} &7| &8{check.name} &7| <white>{passed-time:formatted}"
                    ),
                    ColoredPane.read(node.node("material", "colored-pane")),
                    ConfigurationUtil.material(
                            node.node("material", "otherwise"),
                            Material.WHITE_STAINED_GLASS_PANE
                    ),
                    node.node("name").getString(
                            "<white>Средняя вероятность: {statistic.average-probability:colored}"
                    ),
                    ConfigurationUtil.strings(node.node("lore", "header")),
                    ConfigurationUtil.strings(node.node("lore", "footer")),
                    node.node("update-on-failure-receive").getBoolean(true),
                    ConfigurationUtil.strings(node.node("on-left-click")),
                    ConfigurationUtil.strings(node.node("on-right-click"))
            );
        }
    }

    public record ColoredPane(
            boolean enabled,
            @NotNull ValueSource valueSource
    ) {

        private static @NotNull ColoredPane read(
                final @NotNull ConfigurationNode node
        ) {
            final String color = node.node("color").getString("probability");

            try {
                return new ColoredPane(
                        node.node("enabled").getBoolean(true),
                        ValueSource.valueOf(color.trim().toUpperCase(Locale.ROOT))
                );
            } catch (final IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "entry-template.material.colored-pane.color must be 'probability'",
                        exception
                );
            }
        }
    }

    public enum ValueSource {
        PROBABILITY
    }
}

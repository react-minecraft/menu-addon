package com.g4vrk.menuAddon.config;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TargetSelectorConfiguration {

    private final String title;
    private final int size;
    private final boolean onlyOnline;
    private final SortBy sortBy;
    private final boolean descending;
    private final List<Integer> allowedSlots;
    private final TargetItem targetItem;
    private final Map<String, ItemConfiguration> items;

    public TargetSelectorConfiguration(
            final @NotNull ConfigurationNode node
    ) {
        this.title = node.node("title").getString("&8Нарушители");
        this.size = ConfigurationUtil.inventorySize(node.node("size"));
        this.onlyOnline = node.node("listing", "only-online").getBoolean(true);
        this.sortBy = SortBy.read(node.node("listing", "sort", "by").getString("latest-failure"));
        this.descending = node.node("listing", "sort", "descending").getBoolean(true);
        this.allowedSlots = ConfigurationUtil.slots(
                node.node("listing", "allowed-slots"),
                this.size
        );
        this.targetItem = TargetItem.read(node.node("target-template"));
        this.items = readItems(node.node("items"), this.size);
    }

    public @NotNull String title() {
        return this.title;
    }

    public int size() {
        return this.size;
    }

    public boolean onlyOnline() {
        return this.onlyOnline;
    }

    public @NotNull SortBy sortBy() {
        return this.sortBy;
    }

    public boolean descending() {
        return this.descending;
    }

    public @NotNull List<Integer> allowedSlots() {
        return this.allowedSlots;
    }

    public @NotNull TargetItem targetItem() {
        return this.targetItem;
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

    public enum SortBy {
        LATEST_FAILURE,
        AVERAGE_PROBABILITY,
        AVERAGE_CONFIDENCE,
        NAME;

        private static @NotNull SortBy read(
                final @NotNull String value
        ) {
            try {
                return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown target sorting mode: " + value, exception);
            }
        }
    }

    public record TargetItem(
            boolean usePlayerHead,
            @NotNull Material material,
            @NotNull String name,
            @NotNull List<String> lore,
            @NotNull FailureList failures,
            boolean updateOnFailure,
            @NotNull List<String> leftClickActions,
            @NotNull List<String> rightClickActions
    ) {

        private static @NotNull TargetItem read(
                final @NotNull ConfigurationNode node
        ) {
            return new TargetItem(
                    node.node("material", "use-player-head").getBoolean(true),
                    ConfigurationUtil.material(
                            node.node("material", "otherwise"),
                            Material.PLAYER_HEAD
                    ),
                    node.node("name").getString("<white>{player.name}"),
                    ConfigurationUtil.strings(node.node("lore")),
                    FailureList.read(node.node("failures")),
                    node.node("update-on-failure-receive").getBoolean(true),
                    ConfigurationUtil.strings(node.node("on-left-click")),
                    ConfigurationUtil.strings(node.node("on-right-click"))
            );
        }
    }

    public record FailureList(
            int max,
            int perLine,
            boolean newestOnLeft,
            @NotNull String decimalFormat,
            @NotNull String format,
            @NotNull String separator,
            @NotNull String empty
    ) {

        private static @NotNull FailureList read(
                final @NotNull ConfigurationNode node
        ) {
            final String appending = node.node("appending").getString("left");

            if (!appending.equalsIgnoreCase("left") && !appending.equalsIgnoreCase("right")) {
                throw new IllegalArgumentException("failures.appending must be 'left' or 'right'");
            }

            return new FailureList(
                    Math.max(0, node.node("max").getInt(15)),
                    Math.max(1, node.node("per-line").getInt(5)),
                    appending.equalsIgnoreCase("left"),
                    ConfigurationUtil.decimalFormat(node.node("decimal-format"), "0.00000"),
                    node.node("format").getString("{probability:colored}"),
                    node.node("separator").getString(" "),
                    node.node("empty").getString("&7Нет данных")
            );
        }
    }
}

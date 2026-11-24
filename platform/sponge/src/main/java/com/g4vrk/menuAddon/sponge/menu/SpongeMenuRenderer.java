package com.g4vrk.menuAddon.sponge.menu;

import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.menu.render.MenuItemData;
import com.g4vrk.menuAddon.sponge.item.SpongeMenuItemFactory;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.item.inventory.ContainerType;
import org.spongepowered.api.item.inventory.ContainerTypes;
import org.spongepowered.api.item.inventory.type.ViewableInventory;
import org.spongepowered.plugin.PluginContainer;

import java.util.Map;
import java.util.List;

/**
 * Creates Sponge inventories and renders their configured static items.
 */
public final class SpongeMenuRenderer {

    private SpongeMenuRenderer() {

    }

    public static @NotNull ViewableInventory players(
            final @NotNull PluginContainer plugin,
            final @NotNull MenuTextRenderer renderer,
            final @NotNull TargetSelectorConfiguration selector,
            final @NotNull List<TrackedPlayer> tracked,
            final @NotNull List<Integer> slots,
            final int page,
            final int pages
    ) {

        final ViewableInventory inventory = render(selector.size(), plugin, renderer, selector.items(),
                renderer.menuPlaceholders(page, pages, null));
        for (int index = page * slots.size(); index < Math.min((page + 1) * slots.size(), tracked.size()); index++) {
            final TrackedPlayer target = tracked.get(index);
            inventory.set(slots.get(index - page * slots.size()), SpongeMenuItemFactory.item(renderer.targetItem(
                    selector.targetItem(), target, target.history()
            ), target.name()));
        }
        return inventory;

    }

    public static @NotNull ViewableInventory history(
            final @NotNull PluginContainer plugin,
            final @NotNull MenuTextRenderer renderer,
            final @NotNull SelectedTargetConfiguration selected,
            final @NotNull TrackedPlayer target,
            final @NotNull List<List<HistoryEntry>> groups,
            final @NotNull List<Integer> slots,
            final int page,
            final int pages
    ) {

        final ViewableInventory inventory = render(selected.size(), plugin, renderer, selected.items(),
                renderer.menuPlaceholders(page, pages, target));
        for (int index = page * slots.size(); index < Math.min((page + 1) * slots.size(), groups.size()); index++) {
            inventory.set(slots.get(index - page * slots.size()), SpongeMenuItemFactory.item(renderer.historyGroupItem(
                    selected.entryItem(), target, groups.get(index), renderer.menuPlaceholders(page, pages, target)
            )));
        }
        return inventory;

    }

    private static @NotNull ViewableInventory render(
            final int size,
            final @NotNull PluginContainer plugin,
            final @NotNull MenuTextRenderer renderer,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders
    ) {

        final ViewableInventory inventory = ViewableInventory.builder()
                .type(containerType(size))
                .completeStructure()
                .plugin(plugin)
                .build();
        for (final ItemConfiguration configured : items.values()) {
            final MenuItemData rendered = renderer.staticItem(configured, placeholders);
            for (final int slot : configured.slots()) {
                inventory.set(slot, SpongeMenuItemFactory.item(rendered));
            }
        }
        return inventory;

    }

    private static @NotNull ContainerType containerType(final int size) {

        return switch (size / 9) {
            case 1 -> ContainerTypes.GENERIC_9X1.get();
            case 2 -> ContainerTypes.GENERIC_9X2.get();
            case 3 -> ContainerTypes.GENERIC_9X3.get();
            case 4 -> ContainerTypes.GENERIC_9X4.get();
            case 5 -> ContainerTypes.GENERIC_9X5.get();
            default -> ContainerTypes.GENERIC_9X6.get();
        };

    }

}

package com.g4vrk.menuAddon.menu.bukkit;

import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.menu.MenuActionExecutor;
import com.g4vrk.menuAddon.menu.MenuHolder;
import com.g4vrk.menuAddon.menu.MenuLayout;
import com.g4vrk.menuAddon.menu.action.CompiledMenuAction;
import com.g4vrk.menuAddon.menu.action.MenuActionTable;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Renders target selector and selected target menus into Bukkit inventories.
 */
public final class BukkitMenuRenderer {

    private final MenuActionExecutor actionExecutor;
    private final PermissionChecker permissionChecker;

    public BukkitMenuRenderer(
            final @NotNull MenuActionExecutor actionExecutor,
            final @NotNull PermissionChecker permissionChecker
    ) {

        this.actionExecutor = actionExecutor;
        this.permissionChecker = permissionChecker;

    }

    public void renderTargetSelector(
            final @NotNull MenuHolder holder,
            final @NotNull TargetSelectorConfiguration config,
            final @NotNull MenuTextRenderer renderer,
            final @NotNull MenuActionTable<Player> actions,
            final @NotNull List<TrackedPlayer> targets
    ) {

        final List<Integer> slots = MenuLayout.contentSlots(config.allowedSlots(), config.items());
        final int totalPages = MenuLayout.pages(targets.size(), slots.size());
        final int page = MenuLayout.clampPage(holder.page(), totalPages);
        final Map<String, String> menuPlaceholders = renderer.menuPlaceholders(page, totalPages, null);

        holder.reset(page, totalPages);
        this.renderStaticItems(holder, config.items(), menuPlaceholders, null, renderer, actions);

        final int offset = page * slots.size();
        final int amount = Math.min(slots.size(), targets.size() - offset);

        for (int index = 0; index < amount; index++) {
            final int slot = slots.get(index);
            final TrackedPlayer target = targets.get(offset + index);
            final List<HistoryEntry> history = target.history();

            holder.getInventory().setItem(
                    slot,
                    BukkitMenuItemRenderer.render(
                            renderer.targetItem(config.targetItem(), target, history)
                    )
            );
            holder.addTimeUpdate(() -> {
                final ItemStack current = holder.getInventory().getItem(slot);
                if (current != null) {
                    BukkitMenuItemRenderer.updateLore(
                            current,
                            renderer.targetLore(config.targetItem(), target, history)
                    );
                    holder.getInventory().setItem(slot, current);
                }
            });
            this.bind(
                    holder,
                    slot,
                    actions.resolve(config.targetItem().leftClickActions()),
                    actions.resolve(config.targetItem().rightClickActions()),
                    target,
                    renderer.menuPlaceholders(page, totalPages, target)
            );
        }

    }

    public void renderSelectedTarget(
            final @NotNull MenuHolder holder,
            final @NotNull SelectedTargetConfiguration config,
            final @NotNull MenuTextRenderer renderer,
            final @NotNull MenuActionTable<Player> actions,
            final @NotNull TrackedPlayer target,
            final @NotNull List<List<HistoryEntry>> groups
    ) {

        final List<Integer> slots = MenuLayout.contentSlots(config.allowedSlots(), config.items());
        final int totalPages = MenuLayout.pages(groups.size(), slots.size());
        final int page = MenuLayout.clampPage(holder.page(), totalPages);
        final Map<String, String> menuPlaceholders = renderer.menuPlaceholders(page, totalPages, target);

        holder.reset(page, totalPages);
        this.renderStaticItems(holder, config.items(), menuPlaceholders, target, renderer, actions);

        final int offset = page * slots.size();
        final int amount = Math.min(slots.size(), groups.size() - offset);

        for (int index = 0; index < amount; index++) {
            final int slot = slots.get(index);
            final List<HistoryEntry> entries = groups.get(offset + index);

            holder.getInventory().setItem(
                    slot,
                    BukkitMenuItemRenderer.render(
                            renderer.historyGroupItem(config.entryItem(), target, entries, menuPlaceholders)
                    )
            );
            holder.addTimeUpdate(() -> {
                final ItemStack current = holder.getInventory().getItem(slot);
                if (current != null) {
                    BukkitMenuItemRenderer.updateLore(
                            current,
                            renderer.historyGroupLore(config.entryItem(), target, entries, menuPlaceholders)
                    );
                    holder.getInventory().setItem(slot, current);
                }
            });
            this.bind(
                    holder,
                    slot,
                    actions.resolve(config.entryItem().leftClickActions()),
                    actions.resolve(config.entryItem().rightClickActions()),
                    target,
                    menuPlaceholders
            );
        }

    }

    private void renderStaticItems(
            final @NotNull MenuHolder holder,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders,
            final @Nullable TrackedPlayer target,
            final @NotNull MenuTextRenderer renderer,
            final @NotNull MenuActionTable<Player> actions
    ) {

        for (final ItemConfiguration item : items.values()) {
            final ItemStack rendered = BukkitMenuItemRenderer.render(renderer.staticItem(item, placeholders));
            final List<CompiledMenuAction<Player>> leftActions = actions.resolve(item.leftClickActions());
            final List<CompiledMenuAction<Player>> rightActions = actions.resolve(item.rightClickActions());

            for (final int slot : item.slots()) {
                holder.getInventory().setItem(slot, rendered);
                this.bind(holder, slot, leftActions, rightActions, target, placeholders);
            }
        }

    }

    private void bind(
            final @NotNull MenuHolder holder,
            final int slot,
            final @NotNull List<CompiledMenuAction<Player>> leftClickActions,
            final @NotNull List<CompiledMenuAction<Player>> rightClickActions,
            final @Nullable TrackedPlayer target,
            final @NotNull Map<String, String> placeholders
    ) {

        holder.bind(slot, (viewer, clickType) -> {
            if (!this.permissionChecker.canView(viewer)) {
                viewer.closeInventory();
                return;
            }

            final List<CompiledMenuAction<Player>> actions = clickActions(
                    clickType,
                    leftClickActions,
                    rightClickActions
            );

            if (actions.isEmpty()) {
                return;
            }

            this.actionExecutor.execute(
                    actions,
                    new MenuActionExecutor.Context(
                            viewer,
                            target,
                            holder.type(),
                            holder.page(),
                            holder.totalPages(),
                            placeholders
                    )
            );
        });

    }

    private static @NotNull List<CompiledMenuAction<Player>> clickActions(
            final @NotNull ClickType clickType,
            final @NotNull List<CompiledMenuAction<Player>> leftClickActions,
            final @NotNull List<CompiledMenuAction<Player>> rightClickActions
    ) {

        if (clickType.isLeftClick()) {
            return leftClickActions;
        }

        if (clickType.isRightClick()) {
            return rightClickActions;
        }

        return List.of();

    }

    /**
     * Checks whether a viewer may still interact with an open menu.
     */
    @FunctionalInterface
    public interface PermissionChecker {

        boolean canView(@NotNull Player viewer);

    }

}

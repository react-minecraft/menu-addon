package com.g4vrk.menuAddon.minestom.view;

import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.menu.render.MenuItemData;
import com.g4vrk.menuAddon.minestom.action.MenuActionHandler;
import com.g4vrk.menuAddon.minestom.item.MenuItemRenderer;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.click.Click;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens and refreshes Minestom menu inventories and handles their clicks.
 */
public final class MenuView {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final AddonEnvironment environment;
    private final MenuActionHandler actionHandler;
    private String commandPermission;
    private boolean includeEmptyHistory;
    private TargetSelectorConfiguration selector;
    private SelectedTargetConfiguration selected;
    private MenuTextRenderer renderer;

    public MenuView(final @NotNull AddonEnvironment environment) {

        this.environment = environment;
        this.actionHandler = new MenuActionHandler(environment, this);

    }

    public void configure(
            final @NotNull String commandPermission,
            final boolean includeEmptyHistory,
            final @NotNull TargetSelectorConfiguration selector,
            final @NotNull SelectedTargetConfiguration selected,
            final @NotNull MenuTextRenderer renderer
    ) {

        this.commandPermission = commandPermission;
        this.includeEmptyHistory = includeEmptyHistory;
        this.selector = selector;
        this.selected = selected;
        this.renderer = renderer;

    }

    public void close() {

        for (final Session session : this.sessions.values()) {
            session.player().closeInventory();
        }
        this.sessions.clear();

    }

    public void openPlayers(final @NotNull Player viewer, final int requestedPage) {

        final List<TrackedPlayer> tracked = new ArrayList<>();
        this.environment.players().forEach(target -> {
            if (this.includeEmptyHistory || !target.history().isEmpty()) {
                tracked.add(target);
            }
        });
        final Comparator<TrackedPlayer> ordering = switch (this.selector.sortBy()) {
            case NAME -> Comparator.comparing(TrackedPlayer::name, String.CASE_INSENSITIVE_ORDER);
            case AVERAGE_PROBABILITY -> Comparator.comparingDouble(MenuTextRenderer::averageProbability);
            case LATEST_FAILURE -> Comparator.comparingLong(MenuView::latestFailure);
        };
        tracked.sort(this.selector.descending() ? ordering.reversed() : ordering);
        final List<Integer> slots = contentSlots(this.selector.allowedSlots(), this.selector.items());
        final int pages = pages(tracked.size(), slots.size());
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));
        final Inventory inventory = inventory(this.renderer.title(
                this.selector.title(), this.renderer.menuPlaceholders(page, pages, null)
        ), this.selector.size());
        renderStatic(inventory, this.selector.items(), this.renderer.menuPlaceholders(page, pages, null));
        final int start = page * slots.size();
        final int end = Math.min(start + slots.size(), tracked.size());
        for (int index = start; index < end; index++) {
            final TrackedPlayer target = tracked.get(index);
            inventory.setItemStack(slots.get(index - start), MenuItemRenderer.item(this.renderer.targetItem(
                    this.selector.targetItem(), target, target.history()
            ), target.name()));
        }
        this.sessions.put(viewer.getUuid(), Session.players(viewer, inventory, page, pages, tracked));
        viewer.openInventory(inventory);

    }

    public void openHistory(
            final @NotNull Player viewer,
            final @NotNull TrackedPlayer target,
            final int requestedPage
    ) {

        final List<List<HistoryEntry>> groups = historyGroups(target, this.selected.entryItem().failuresPerEntry());
        final List<Integer> slots = contentSlots(this.selected.allowedSlots(), this.selected.items());
        final int pages = pages(groups.size(), slots.size());
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));
        final Inventory inventory = inventory(this.renderer.title(
                this.selected.title(), this.renderer.menuPlaceholders(page, pages, target)
        ), this.selected.size());
        renderStatic(inventory, this.selected.items(), this.renderer.menuPlaceholders(page, pages, target));
        final int start = page * slots.size();
        final int end = Math.min(start + slots.size(), groups.size());
        for (int index = start; index < end; index++) {
            inventory.setItemStack(slots.get(index - start), MenuItemRenderer.item(this.renderer.historyGroupItem(
                    this.selected.entryItem(), target, groups.get(index), this.renderer.menuPlaceholders(page, pages, target)
            )));
        }
        this.sessions.put(viewer.getUuid(), Session.history(viewer, inventory, page, pages, target));
        viewer.openInventory(inventory);

    }

    public void onInventoryClick(final @NotNull InventoryPreClickEvent event) {

        final Session session = this.sessions.get(event.getPlayer().getUuid());
        if (session == null || session.inventory() != event.getInventory()) {
            return;
        }
        event.setCancelled(true);
        final PlatformPlayer platformPlayer = this.environment.platform().adaptPlayer(event.getPlayer());
        if (!this.commandPermission.isEmpty() && !platformPlayer.hasPermission(this.commandPermission)) {
            event.getPlayer().closeInventory();
            return;
        }
        final int slot = event.getSlot();
        if (session.historyTarget() == null) {
            final List<Integer> slots = contentSlots(this.selector.allowedSlots(), this.selector.items());
            final int contentIndex = slots.indexOf(slot);
            if (contentIndex >= 0) {
                final int targetIndex = session.page() * slots.size() + contentIndex;
                if (targetIndex < session.players().size()) {
                    final TrackedPlayer target = session.players().get(targetIndex);
                    final boolean leftClick = event.getClick() instanceof Click.Left;
                    this.actionHandler.execute(this.renderer, event.getPlayer(), leftClick
                                    ? this.selector.targetItem().leftClickActions()
                                    : this.selector.targetItem().rightClickActions(),
                            this.renderer.menuPlaceholders(0, 1, target), target, 0);
                }
            } else {
                runConfiguredActions(event.getPlayer(), slot, session.page(), session.pages(), null,
                        event.getClick() instanceof Click.Left);
            }
            return;
        }
        final List<Integer> slots = contentSlots(this.selected.allowedSlots(), this.selected.items());
        if (slots.contains(slot)) {
            this.actionHandler.execute(this.renderer, event.getPlayer(), event.getClick() instanceof Click.Left
                            ? this.selected.entryItem().leftClickActions()
                            : this.selected.entryItem().rightClickActions(),
                    this.renderer.menuPlaceholders(session.page(), session.pages(), session.historyTarget()),
                    session.historyTarget(), session.page());
        } else {
            runConfiguredActions(event.getPlayer(), slot, session.page(), session.pages(), session.historyTarget(),
                    event.getClick() instanceof Click.Left);
        }

    }

    public void onInventoryClose(final @NotNull InventoryCloseEvent event) {

        final Session session = this.sessions.get(event.getPlayer().getUuid());
        if (session != null && session.inventory() == event.getInventory()) {
            this.sessions.remove(event.getPlayer().getUuid());
        }

    }

    private static long latestFailure(final @NotNull TrackedPlayer player) {

        final List<HistoryEntry> entries = player.history();
        return entries.isEmpty() ? Long.MIN_VALUE : entries.get(entries.size() - 1).timestamp();

    }

    public void refreshSessions() {

        for (final Session session : List.copyOf(this.sessions.values())) {
            if (session.historyTarget() == null) {
                openPlayers(session.player(), session.page());
            } else {
                openHistory(session.player(), session.historyTarget(), session.page());
            }
        }

    }

    @Subscribe(priority = EventPriority.MONITOR)
    public void onInferenceResult(final @NotNull InferenceResultEvent event) {

        final TrackedPlayer changed = event.check().player();
        for (final Session session : List.copyOf(this.sessions.values())) {
            this.environment.scheduler().<Player>mapEntity(
                    this.environment.platform()::adaptPlayer
            ).scheduleEntity(session.player(), () -> {
                if (session.historyTarget() == null) {
                    if (this.selector.targetItem().updateOnFailure()) {
                        this.openPlayers(session.player(), session.page());
                    }
                } else if (this.selected.entryItem().updateOnFailure()
                        && session.historyTarget().uniqueId().equals(changed.uniqueId())) {
                    this.openHistory(session.player(), changed, session.page());
                }
            }, TickSchedule.instant());
        }

    }

    private static @NotNull Inventory inventory(final @NotNull Component title, final int size) {

        final InventoryType type = switch (size / 9) {
            case 1 -> InventoryType.CHEST_1_ROW;
            case 2 -> InventoryType.CHEST_2_ROW;
            case 3 -> InventoryType.CHEST_3_ROW;
            case 4 -> InventoryType.CHEST_4_ROW;
            case 5 -> InventoryType.CHEST_5_ROW;
            default -> InventoryType.CHEST_6_ROW;
        };
        return new Inventory(type, title);

    }

    private static @NotNull List<Integer> contentSlots(
            final @NotNull List<Integer> configured,
            final @NotNull Map<String, ItemConfiguration> items
    ) {

        final List<Integer> result = new ArrayList<>(configured);
        for (final ItemConfiguration item : items.values()) {
            result.removeAll(item.slots());
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Menu has no content slots after static items are reserved");
        }
        return List.copyOf(result);

    }

    private static int pages(final int content, final int capacity) {

        return Math.max(1, (content + capacity - 1) / capacity);

    }

    private void runConfiguredActions(
            final @NotNull Player viewer,
            final int slot,
            final int page,
            final int pageCount,
            final @Nullable TrackedPlayer target,
            final boolean leftClick
    ) {

        final Map<String, ItemConfiguration> items = target == null ? this.selector.items() : this.selected.items();
        final Map<String, String> placeholders = this.renderer.menuPlaceholders(page, pageCount, target);
        for (final ItemConfiguration item : items.values()) {
            if (!item.slots().contains(slot)) {
                continue;
            }
            this.actionHandler.execute(this.renderer, viewer, leftClick ? item.leftClickActions() : item.rightClickActions(),
                    placeholders, target, page);
        }

    }

    private void renderStatic(
            final @NotNull Inventory inventory,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders
    ) {

        for (final ItemConfiguration item : items.values()) {
            final MenuItemData rendered = this.renderer.staticItem(item, placeholders);
            for (final int slot : item.slots()) {
                inventory.setItemStack(slot, MenuItemRenderer.item(rendered));
            }
        }

    }

    private static @NotNull List<List<HistoryEntry>> historyGroups(
            final @NotNull TrackedPlayer target,
            final int groupSize
    ) {

        final List<HistoryEntry> history = target.history();
        final List<List<HistoryEntry>> groups = new ArrayList<>();
        for (int end = history.size(); end > 0; end -= groupSize) {
            final int start = Math.max(0, end - groupSize);
            final List<HistoryEntry> group = new ArrayList<>(history.subList(start, end));
            Collections.reverse(group);
            groups.add(List.copyOf(group));
        }
        return groups;

    }

    /**
     * Tracks a viewer and the currently open inventory.
     */
    private record Session(
            @NotNull Player player,
            @NotNull Inventory inventory,
            int page,
            int pages,
            @NotNull List<TrackedPlayer> players,
            @Nullable TrackedPlayer historyTarget
    ) {

        private static Session players(
                final @NotNull Player player,
                final @NotNull Inventory inventory,
                final int page,
                final int pages,
                final @NotNull List<TrackedPlayer> targets
        ) {

            return new Session(player, inventory, page, pages, List.copyOf(targets), null);

        }

        private static Session history(
                final @NotNull Player player,
                final @NotNull Inventory inventory,
                final int page,
                final int pages,
                final @NotNull TrackedPlayer target
        ) {

            return new Session(player, inventory, page, pages, List.of(), target);

        }

    }

}

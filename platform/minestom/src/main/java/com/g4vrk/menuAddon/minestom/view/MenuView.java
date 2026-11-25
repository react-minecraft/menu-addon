package com.g4vrk.menuAddon.minestom.view;

import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.menu.MenuLayout;
import com.g4vrk.menuAddon.menu.MenuType;
import com.g4vrk.menuAddon.menu.action.MenuActionCompiler;
import com.g4vrk.menuAddon.menu.action.MenuActionTable;
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
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private final TaskScheduler<Player> scheduler;
    private final MenuActionHandler actionHandler;
    private final MenuActionCompiler<Void> actionCompiler;

    private volatile State state;

    public MenuView(
            final @NotNull AddonEnvironment environment
    ) {

        this.environment = environment;
        this.scheduler = environment.scheduler().mapEntity(environment.platform()::adaptPlayer);
        this.actionHandler = new MenuActionHandler(environment, this);
        this.actionCompiler = new MenuActionCompiler<>(environment.actions(), null, null);

    }

    public void configure(
            final @NotNull String commandPermission,
            final boolean includeEmptyHistory,
            final @NotNull TargetSelectorConfiguration selector,
            final @NotNull SelectedTargetConfiguration selected,
            final @NotNull MenuTextRenderer renderer
    ) {

        MenuLayout.validate(selector, selected);
        final MenuActionTable<Void> compiledActions = this.actionCompiler.compileTable(selector, selected);
        this.state = new State(commandPermission, includeEmptyHistory, selector, selected, renderer, compiledActions);

    }

    public void close() {

        for (final Session session : List.copyOf(this.sessions.values())) {
            session.player().closeInventory();
        }
        this.sessions.clear();

    }

    public void openPlayers(
            final @NotNull Player viewer,
            final int requestedPage
    ) {

        final State state = this.state;
        final List<TrackedPlayer> tracked = MenuLayout.targets(
                this.environment.players(),
                state.includeEmptyHistory(),
                state.selector()
        );
        final List<Integer> slots = MenuLayout.contentSlots(
                state.selector().allowedSlots(),
                state.selector().items()
        );
        final int pages = MenuLayout.pages(tracked.size(), slots.size());
        final int page = MenuLayout.clampPage(requestedPage, pages);
        final Inventory inventory = inventory(
                state.renderer().title(
                        state.selector().title(),
                        state.renderer().menuPlaceholders(page, pages, null)
                ),
                state.selector().size()
        );
        populatePlayers(inventory, state, tracked, slots, page, pages);
        this.sessions.put(viewer.getUuid(), Session.players(viewer, inventory, page, pages, tracked));
        viewer.openInventory(inventory);

    }

    public void openHistory(
            final @NotNull Player viewer,
            final @NotNull TrackedPlayer target,
            final int requestedPage
    ) {

        final State state = this.state;
        final List<List<HistoryEntry>> groups = MenuLayout.historyGroups(
                target,
                state.selected().entryItem().failuresPerEntry()
        );
        final List<Integer> slots = MenuLayout.contentSlots(
                state.selected().allowedSlots(),
                state.selected().items()
        );
        final int pages = MenuLayout.pages(groups.size(), slots.size());
        final int page = MenuLayout.clampPage(requestedPage, pages);
        final Inventory inventory = inventory(
                state.renderer().title(
                        state.selected().title(),
                        state.renderer().menuPlaceholders(page, pages, target)
                ),
                state.selected().size()
        );
        populateHistory(inventory, state, target, groups, slots, page, pages);
        this.sessions.put(viewer.getUuid(), Session.history(viewer, inventory, page, pages, target));
        viewer.openInventory(inventory);

    }

    public void onInventoryClick(
            final @NotNull InventoryPreClickEvent event
    ) {

        final Session session = this.sessions.get(event.getPlayer().getUuid());
        if (session == null || session.inventory() != event.getInventory()) {
            return;
        }
        event.setCancelled(true);

        final State state = this.state;
        final PlatformPlayer platformPlayer = this.environment.platform().adaptPlayer(event.getPlayer());
        if (!state.commandPermission().isEmpty() && !platformPlayer.hasPermission(state.commandPermission())) {
            event.getPlayer().closeInventory();
            return;
        }

        final int slot = event.getSlot();
        final boolean leftClick = event.getClick() instanceof Click.Left;
        final boolean rightClick = event.getClick() instanceof Click.Right;
        if (!leftClick && !rightClick) {
            return;
        }

        if (session.menuType() == MenuType.TARGET_SELECTOR) {
            final List<Integer> slots = MenuLayout.contentSlots(
                    state.selector().allowedSlots(),
                    state.selector().items()
            );
            final int contentIndex = slots.indexOf(slot);
            if (contentIndex >= 0) {
                final int targetIndex = session.page() * slots.size() + contentIndex;
                if (targetIndex < session.players().size()) {
                    final TrackedPlayer target = session.players().get(targetIndex);
                    final List<String> rawActions = leftClick
                            ? state.selector().targetItem().leftClickActions()
                            : state.selector().targetItem().rightClickActions();
                    this.actionHandler.execute(
                            event.getPlayer(),
                            state.actions().resolve(rawActions),
                            state.renderer().menuPlaceholders(session.page(), session.pages(), target),
                            target,
                            MenuType.TARGET_SELECTOR
                    );
                }
            } else {
                this.runConfiguredActions(
                        event.getPlayer(),
                        state,
                        slot,
                        session.page(),
                        session.pages(),
                        null,
                        leftClick
                );
            }
            return;
        }

        final List<Integer> slots = MenuLayout.contentSlots(
                state.selected().allowedSlots(),
                state.selected().items()
        );
        final TrackedPlayer target = session.historyTarget();
        if (slots.contains(slot)) {
            final List<String> rawActions = leftClick
                    ? state.selected().entryItem().leftClickActions()
                    : state.selected().entryItem().rightClickActions();
            this.actionHandler.execute(
                    event.getPlayer(),
                    state.actions().resolve(rawActions),
                    state.renderer().menuPlaceholders(session.page(), session.pages(), target),
                    target,
                    MenuType.SELECTED_TARGET
            );
        } else {
            this.runConfiguredActions(
                    event.getPlayer(),
                    state,
                    slot,
                    session.page(),
                    session.pages(),
                    target,
                    leftClick
            );
        }

    }

    public void onInventoryClose(
            final @NotNull InventoryCloseEvent event
    ) {

        final Session session = this.sessions.get(event.getPlayer().getUuid());
        if (session != null && session.inventory() == event.getInventory()) {
            this.sessions.remove(event.getPlayer().getUuid(), session);
        }

    }

    public void onPlayerDisconnect(
            final @NotNull PlayerDisconnectEvent event
    ) {

        this.sessions.remove(event.getPlayer().getUuid());

    }

    public void refreshSessions() {

        for (final Session session : List.copyOf(this.sessions.values())) {
            if (!this.isCurrent(session)) {
                continue;
            }
            if (session.menuType() == MenuType.TARGET_SELECTOR) {
                openPlayers(session.player(), session.page());
            } else if (session.historyTarget() != null) {
                openHistory(session.player(), session.historyTarget(), session.page());
            }
        }

    }

    @Subscribe(priority = EventPriority.MONITOR)
    public void onInferenceResult(
            final @NotNull InferenceResultEvent event
    ) {

        final TrackedPlayer changed = event.check().player();
        for (final Session scheduledSession : List.copyOf(this.sessions.values())) {
            this.scheduler.scheduleEntity(
                    scheduledSession.player(),
                    () -> this.refreshSessionInPlace(scheduledSession, changed),
                    TickSchedule.instant()
            );
        }

    }

    private void refreshSessionInPlace(
            final @NotNull Session expectedSession,
            final @NotNull TrackedPlayer changed
    ) {

        final Session current = this.sessions.get(expectedSession.player().getUuid());
        if (current == null || current.inventory() != expectedSession.inventory() || !this.isCurrent(current)) {
            return;
        }

        final State state = this.state;
        if (current.menuType() == MenuType.TARGET_SELECTOR) {
            if (!state.selector().targetItem().updateOnFailure()) {
                return;
            }
            if (current.inventory().getSize() != state.selector().size()) {
                this.openPlayers(current.player(), current.page());
                return;
            }
            final List<TrackedPlayer> tracked = MenuLayout.targets(
                    this.environment.players(),
                    state.includeEmptyHistory(),
                    state.selector()
            );
            final List<Integer> slots = MenuLayout.contentSlots(
                    state.selector().allowedSlots(),
                    state.selector().items()
            );
            final int pages = MenuLayout.pages(tracked.size(), slots.size());
            final int page = MenuLayout.clampPage(current.page(), pages);
            populatePlayers(current.inventory(), state, tracked, slots, page, pages);
            this.sessions.replace(
                    current.player().getUuid(),
                    current,
                    Session.players(current.player(), current.inventory(), page, pages, tracked)
            );
            return;
        }

        if (current.historyTarget() == null
                || !state.selected().entryItem().updateOnFailure()
                || !current.historyTarget().uniqueId().equals(changed.uniqueId())) {
            return;
        }
        if (current.inventory().getSize() != state.selected().size()) {
            this.openHistory(current.player(), changed, current.page());
            return;
        }
        final List<List<HistoryEntry>> groups = MenuLayout.historyGroups(
                changed,
                state.selected().entryItem().failuresPerEntry()
        );
        final List<Integer> slots = MenuLayout.contentSlots(
                state.selected().allowedSlots(),
                state.selected().items()
        );
        final int pages = MenuLayout.pages(groups.size(), slots.size());
        final int page = MenuLayout.clampPage(current.page(), pages);
        populateHistory(current.inventory(), state, changed, groups, slots, page, pages);
        this.sessions.replace(
                current.player().getUuid(),
                current,
                Session.history(current.player(), current.inventory(), page, pages, changed)
        );

    }

    private boolean isCurrent(
            final @NotNull Session session
    ) {

        final Player player = session.player();
        return this.sessions.get(player.getUuid()) == session
                && player.isOnline()
                && player.getOpenInventory() == session.inventory();

    }

    private static @NotNull Inventory inventory(
            final @NotNull Component title,
            final int size
    ) {

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

    private static void populatePlayers(
            final @NotNull Inventory inventory,
            final @NotNull State state,
            final @NotNull List<TrackedPlayer> tracked,
            final @NotNull List<Integer> slots,
            final int page,
            final int pages
    ) {

        renderStatic(
                inventory,
                state.renderer(),
                state.selector().items(),
                state.renderer().menuPlaceholders(page, pages, null)
        );
        final int start = page * slots.size();
        for (int index = 0; index < slots.size(); index++) {
            final int slot = slots.get(index);
            final int targetIndex = start + index;
            if (targetIndex < tracked.size()) {
                final TrackedPlayer target = tracked.get(targetIndex);
                inventory.setItemStack(slot, MenuItemRenderer.item(
                        state.renderer().targetItem(state.selector().targetItem(), target, target.history()),
                        target.name()
                ));
            } else {
                inventory.setItemStack(slot, ItemStack.AIR);
            }
        }

    }

    private static void populateHistory(
            final @NotNull Inventory inventory,
            final @NotNull State state,
            final @NotNull TrackedPlayer target,
            final @NotNull List<List<HistoryEntry>> groups,
            final @NotNull List<Integer> slots,
            final int page,
            final int pages
    ) {

        final Map<String, String> placeholders = state.renderer().menuPlaceholders(page, pages, target);
        renderStatic(inventory, state.renderer(), state.selected().items(), placeholders);
        final int start = page * slots.size();
        for (int index = 0; index < slots.size(); index++) {
            final int slot = slots.get(index);
            final int groupIndex = start + index;
            if (groupIndex < groups.size()) {
                inventory.setItemStack(slot, MenuItemRenderer.item(state.renderer().historyGroupItem(
                        state.selected().entryItem(),
                        target,
                        groups.get(groupIndex),
                        placeholders
                )));
            } else {
                inventory.setItemStack(slot, ItemStack.AIR);
            }
        }

    }

    private void runConfiguredActions(
            final @NotNull Player viewer,
            final @NotNull State state,
            final int slot,
            final int page,
            final int pageCount,
            final @Nullable TrackedPlayer target,
            final boolean leftClick
    ) {

        final MenuType menuType = target == null ? MenuType.TARGET_SELECTOR : MenuType.SELECTED_TARGET;
        final Map<String, ItemConfiguration> items = menuType == MenuType.TARGET_SELECTOR
                ? state.selector().items() : state.selected().items();
        final Map<String, String> placeholders = state.renderer().menuPlaceholders(page, pageCount, target);

        for (final ItemConfiguration item : items.values()) {
            if (!item.slots().contains(slot)) {
                continue;
            }
            final List<String> rawActions = leftClick ? item.leftClickActions() : item.rightClickActions();
            this.actionHandler.execute(viewer, state.actions().resolve(rawActions), placeholders, target, menuType);
        }

    }

    private static void renderStatic(
            final @NotNull Inventory inventory,
            final @NotNull MenuTextRenderer renderer,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders
    ) {

        for (final ItemConfiguration item : items.values()) {
            final MenuItemData rendered = renderer.staticItem(item, placeholders);
            for (final int slot : item.slots()) {
                inventory.setItemStack(slot, MenuItemRenderer.item(rendered));
            }
        }

    }

    /**
     * Tracks a viewer and the currently open inventory.
     */
    private record Session(
            @NotNull Player player,
            @NotNull Inventory inventory,
            @NotNull MenuType menuType,
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

            return new Session(player, inventory, MenuType.TARGET_SELECTOR, page, pages, List.copyOf(targets), null);

        }

        private static Session history(
                final @NotNull Player player,
                final @NotNull Inventory inventory,
                final int page,
                final int pages,
                final @NotNull TrackedPlayer target
        ) {

            return new Session(player, inventory, MenuType.SELECTED_TARGET, page, pages, List.of(), target);

        }

    }

    /**
     * Immutable template and compiled action snapshot visible across ticks.
     */
    private record State(
            @NotNull String commandPermission,
            boolean includeEmptyHistory,
            @NotNull TargetSelectorConfiguration selector,
            @NotNull SelectedTargetConfiguration selected,
            @NotNull MenuTextRenderer renderer,
            @NotNull MenuActionTable<Void> actions
    ) {
    }

}

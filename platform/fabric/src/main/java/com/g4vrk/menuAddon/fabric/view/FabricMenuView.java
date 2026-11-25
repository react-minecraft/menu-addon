package com.g4vrk.menuAddon.fabric.view;

import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.fabric.action.FabricMenuActionExecutor;
import com.g4vrk.menuAddon.fabric.item.FabricMenuItemRenderer;
import com.g4vrk.menuAddon.fabric.session.FabricMenuSessionManager;
import com.g4vrk.menuAddon.menu.MenuLayout;
import com.g4vrk.menuAddon.menu.MenuType;
import com.g4vrk.menuAddon.menu.action.MenuActionCompiler;
import com.g4vrk.menuAddon.menu.action.MenuActionTable;
import com.g4vrk.menuAddon.menu.render.MenuItemData;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds and refreshes the Fabric player selector and history menus.
 */
public final class FabricMenuView {

    private final AddonEnvironment environment;
    private final TaskScheduler<ServerPlayer> scheduler;
    private final FabricMenuSessionManager sessions;
    private final FabricMenuActionExecutor actions;
    private final MenuActionCompiler<Void> actionCompiler;

    private volatile State state;

    public FabricMenuView(
            final @NotNull AddonEnvironment environment,
            final @NotNull Supplier<String> permission
    ) {

        this.environment = environment;
        this.scheduler = environment.scheduler().mapEntity(environment.platform()::adaptPlayer);
        this.sessions = new FabricMenuSessionManager(environment, permission);
        this.actions = new FabricMenuActionExecutor(environment, this::openPlayers, this::openHistory);
        this.actionCompiler = new MenuActionCompiler<>(environment.actions(), null, null);

    }

    public void configure(
            final boolean includeEmptyHistory,
            final @NotNull TargetSelectorConfiguration selector,
            final @NotNull SelectedTargetConfiguration selected,
            final @NotNull MenuTextRenderer renderer
    ) {

        MenuLayout.validate(selector, selected);
        final MenuActionTable<Void> compiledActions = this.actionCompiler.compileTable(selector, selected);
        this.state = new State(includeEmptyHistory, selector, selected, renderer, compiledActions);

    }

    public void closeAll() {

        this.sessions.closeAll();

    }

    public void openPlayers(
            final @NotNull ServerPlayer viewer,
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
        final SimpleContainer container = new SimpleContainer(state.selector().size());
        populatePlayers(container, state, tracked, slots, page, pages);
        final Component title = FabricMenuItemRenderer.toMinecraft(state.renderer().title(
                state.selector().title(),
                state.renderer().menuPlaceholders(page, pages, null)
        ));
        this.sessions.open(
                viewer,
                title,
                container,
                MenuType.TARGET_SELECTOR,
                page,
                pages,
                tracked,
                null,
                (session, slot, leftClick) -> handleClick(viewer, session, slot, leftClick)
        );

    }

    private void openHistory(
            final @NotNull ServerPlayer viewer,
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
        final SimpleContainer container = new SimpleContainer(state.selected().size());
        populateHistory(container, state, target, groups, slots, page, pages);
        final Component title = FabricMenuItemRenderer.toMinecraft(state.renderer().title(
                state.selected().title(),
                state.renderer().menuPlaceholders(page, pages, target)
        ));
        this.sessions.open(
                viewer,
                title,
                container,
                MenuType.SELECTED_TARGET,
                page,
                pages,
                List.of(),
                target,
                (session, slot, leftClick) -> handleClick(viewer, session, slot, leftClick)
        );

    }

    private void handleClick(
            final @NotNull ServerPlayer viewer,
            final @NotNull FabricMenuSessionManager.Session session,
            final int slot,
            final boolean leftClick
    ) {

        final State state = this.state;
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
                    this.actions.execute(
                            viewer,
                            state.actions().resolve(rawActions),
                            state.renderer().menuPlaceholders(session.page(), session.totalPages(), target),
                            target,
                            MenuType.TARGET_SELECTOR
                    );
                }
            } else {
                clickConfiguredItem(viewer, state, slot, session.page(), session.totalPages(), null, leftClick);
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
            this.actions.execute(
                    viewer,
                    state.actions().resolve(rawActions),
                    state.renderer().menuPlaceholders(session.page(), session.totalPages(), target),
                    target,
                    MenuType.SELECTED_TARGET
            );
        } else {
            clickConfiguredItem(viewer, state, slot, session.page(), session.totalPages(), target, leftClick);
        }

    }

    private static void populatePlayers(
            final @NotNull SimpleContainer container,
            final @NotNull State state,
            final @NotNull List<TrackedPlayer> tracked,
            final @NotNull List<Integer> slots,
            final int page,
            final int pages
    ) {

        renderStatic(
                container,
                state.renderer(),
                state.selector().items(),
                state.renderer().menuPlaceholders(page, pages, null)
        );
        final int offset = page * slots.size();
        for (int index = 0; index < slots.size(); index++) {
            final int slot = slots.get(index);
            final int targetIndex = offset + index;
            if (targetIndex < tracked.size()) {
                final TrackedPlayer target = tracked.get(targetIndex);
                container.setItem(slot, FabricMenuItemRenderer.item(
                        state.renderer().targetItem(state.selector().targetItem(), target, target.history())
                ));
            } else {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }

    }

    private static void populateHistory(
            final @NotNull SimpleContainer container,
            final @NotNull State state,
            final @NotNull TrackedPlayer target,
            final @NotNull List<List<HistoryEntry>> groups,
            final @NotNull List<Integer> slots,
            final int page,
            final int pages
    ) {

        final Map<String, String> placeholders = state.renderer().menuPlaceholders(page, pages, target);
        renderStatic(container, state.renderer(), state.selected().items(), placeholders);
        final int offset = page * slots.size();
        for (int index = 0; index < slots.size(); index++) {
            final int slot = slots.get(index);
            final int groupIndex = offset + index;
            if (groupIndex < groups.size()) {
                container.setItem(slot, FabricMenuItemRenderer.item(state.renderer().historyGroupItem(
                        state.selected().entryItem(),
                        target,
                        groups.get(groupIndex),
                        placeholders
                )));
            } else {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }

    }

    private static void renderStatic(
            final @NotNull SimpleContainer container,
            final @NotNull MenuTextRenderer renderer,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders
    ) {

        for (final ItemConfiguration configured : items.values()) {
            final MenuItemData rendered = renderer.staticItem(configured, placeholders);
            for (final int slot : configured.slots()) {
                container.setItem(slot, FabricMenuItemRenderer.item(rendered));
            }
        }

    }

    private void clickConfiguredItem(
            final @NotNull ServerPlayer viewer,
            final @NotNull State state,
            final int slot,
            final int page,
            final int pages,
            final @Nullable TrackedPlayer target,
            final boolean leftClick
    ) {

        final MenuType menuType = target == null ? MenuType.TARGET_SELECTOR : MenuType.SELECTED_TARGET;
        final Map<String, ItemConfiguration> items = menuType == MenuType.TARGET_SELECTOR
                ? state.selector().items() : state.selected().items();
        final Map<String, String> placeholders = state.renderer().menuPlaceholders(page, pages, target);

        for (final ItemConfiguration item : items.values()) {
            if (!item.slots().contains(slot)) {
                continue;
            }
            final List<String> rawActions = leftClick ? item.leftClickActions() : item.rightClickActions();
            this.actions.execute(viewer, state.actions().resolve(rawActions), placeholders, target, menuType);
        }

    }

    public void refreshSessions() {

        for (final FabricMenuSessionManager.Session session : this.sessions.snapshot()) {
            if (!this.sessions.isCurrent(session)) {
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
        for (final FabricMenuSessionManager.Session scheduledSession : this.sessions.snapshot()) {
            this.scheduler.scheduleEntity(
                    scheduledSession.player(),
                    () -> this.refreshSessionInPlace(scheduledSession, changed),
                    TickSchedule.instant()
            );
        }

    }

    private void refreshSessionInPlace(
            final @NotNull FabricMenuSessionManager.Session expectedSession,
            final @NotNull TrackedPlayer changed
    ) {

        final FabricMenuSessionManager.Session current = this.sessions.current(expectedSession.player().getUUID());
        if (current == null || current.menu() != expectedSession.menu() || !this.sessions.isCurrent(current)) {
            return;
        }

        final State state = this.state;
        if (current.menuType() == MenuType.TARGET_SELECTOR) {
            if (!state.selector().targetItem().updateOnFailure()) {
                return;
            }
            if (current.container().getContainerSize() != state.selector().size()) {
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
            populatePlayers(current.container(), state, tracked, slots, page, pages);
            current.menu().broadcastChanges();
            this.sessions.replace(current, new FabricMenuSessionManager.Session(
                    current.player(),
                    current.menu(),
                    current.container(),
                    MenuType.TARGET_SELECTOR,
                    page,
                    pages,
                    List.copyOf(tracked),
                    null
            ));
            return;
        }

        if (current.historyTarget() == null
                || !state.selected().entryItem().updateOnFailure()
                || !current.historyTarget().uniqueId().equals(changed.uniqueId())) {
            return;
        }
        if (current.container().getContainerSize() != state.selected().size()) {
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
        populateHistory(current.container(), state, changed, groups, slots, page, pages);
        current.menu().broadcastChanges();
        this.sessions.replace(current, new FabricMenuSessionManager.Session(
                current.player(),
                current.menu(),
                current.container(),
                MenuType.SELECTED_TARGET,
                page,
                pages,
                List.of(),
                changed
        ));

    }

    /**
     * Immutable template and compiled action snapshot visible across ticks.
     */
    private record State(
            boolean includeEmptyHistory,
            @NotNull TargetSelectorConfiguration selector,
            @NotNull SelectedTargetConfiguration selected,
            @NotNull MenuTextRenderer renderer,
            @NotNull MenuActionTable<Void> actions
    ) {
    }

}

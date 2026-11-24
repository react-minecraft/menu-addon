package com.g4vrk.menuAddon.fabric.view;

import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.fabric.action.FabricMenuActionExecutor;
import com.g4vrk.menuAddon.fabric.item.FabricMenuItemRenderer;
import com.g4vrk.menuAddon.fabric.session.FabricMenuSessionManager;
import com.g4vrk.menuAddon.menu.render.MenuItemData;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds and refreshes the Fabric player selector and history menus.
 */
public final class FabricMenuView {

    private final AddonEnvironment environment;
    private final FabricMenuSessionManager sessions;
    private final FabricMenuActionExecutor actions;
    private boolean includeEmptyHistory;
    private TargetSelectorConfiguration selector;
    private SelectedTargetConfiguration selected;
    private MenuTextRenderer renderer;

    public FabricMenuView(
            final @NotNull AddonEnvironment environment,
            final @NotNull Supplier<String> permission
    ) {

        this.environment = environment;
        this.sessions = new FabricMenuSessionManager(environment, permission);
        this.actions = new FabricMenuActionExecutor(
                environment, () -> this.renderer, this::openPlayers, this::openHistory
        );

    }

    public void configure(
            final boolean includeEmptyHistory,
            final @NotNull TargetSelectorConfiguration selector,
            final @NotNull SelectedTargetConfiguration selected,
            final @NotNull MenuTextRenderer renderer
    ) {

        this.includeEmptyHistory = includeEmptyHistory;
        this.selector = selector;
        this.selected = selected;
        this.renderer = renderer;

    }

    public void closeAll() {

        this.sessions.closeAll();

    }

    public void openPlayers(final @NotNull ServerPlayer viewer, final int requestedPage) {

        final List<TrackedPlayer> tracked = new ArrayList<>();
        this.environment.players().forEach(target -> {
            if (this.includeEmptyHistory || !target.history().isEmpty()) {
                tracked.add(target);
            }
        });
        final Comparator<TrackedPlayer> ordering = switch (this.selector.sortBy()) {
            case NAME -> Comparator.comparing(TrackedPlayer::name, String.CASE_INSENSITIVE_ORDER);
            case AVERAGE_PROBABILITY -> Comparator.comparingDouble(MenuTextRenderer::averageProbability);
            case LATEST_FAILURE -> Comparator.comparingLong(FabricMenuView::latestFailure);
        };
        tracked.sort(this.selector.descending() ? ordering.reversed() : ordering);
        final List<Integer> slots = contentSlots(this.selector.allowedSlots(), this.selector.items());
        final int pages = pages(tracked.size(), slots.size());
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));
        final SimpleContainer container = new SimpleContainer(this.selector.size());
        renderStatic(container, this.selector.items(), this.renderer.menuPlaceholders(page, pages, null));
        for (int index = page * slots.size(); index < Math.min((page + 1) * slots.size(), tracked.size()); index++) {
            final TrackedPlayer target = tracked.get(index);
            container.setItem(slots.get(index - page * slots.size()), FabricMenuItemRenderer.item(this.renderer.targetItem(
                    this.selector.targetItem(), target, target.history()
            )));
        }
        final Component title = FabricMenuItemRenderer.toMinecraft(this.renderer.title(
                this.selector.title(), this.renderer.menuPlaceholders(page, pages, null)
        ));
        this.sessions.open(viewer, title, container, page, tracked, null, (slot, leftClick) -> {
            final int contentIndex = slots.indexOf(slot);
            if (contentIndex >= 0) {
                final int targetIndex = page * slots.size() + contentIndex;
                if (targetIndex < tracked.size()) {
                    final TrackedPlayer target = tracked.get(targetIndex);
                    this.actions.execute(viewer, leftClick
                                    ? this.selector.targetItem().leftClickActions()
                                    : this.selector.targetItem().rightClickActions(),
                            this.renderer.menuPlaceholders(0, 1, target), target, 0);
                }
            } else {
                clickConfiguredItem(viewer, slot, page, pages, null, leftClick);
            }
        });

    }

    private void openHistory(
            final @NotNull ServerPlayer viewer,
            final @NotNull TrackedPlayer target,
            final int requestedPage
    ) {

        final List<List<HistoryEntry>> groups = historyGroups(target, this.selected.entryItem().failuresPerEntry());
        final List<Integer> slots = contentSlots(this.selected.allowedSlots(), this.selected.items());
        final int pages = pages(groups.size(), slots.size());
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));
        final SimpleContainer container = new SimpleContainer(this.selected.size());
        renderStatic(container, this.selected.items(), this.renderer.menuPlaceholders(page, pages, target));
        for (int index = page * slots.size(); index < Math.min((page + 1) * slots.size(), groups.size()); index++) {
            container.setItem(slots.get(index - page * slots.size()), FabricMenuItemRenderer.item(this.renderer.historyGroupItem(
                    this.selected.entryItem(), target, groups.get(index), this.renderer.menuPlaceholders(page, pages, target)
            )));
        }
        final Component title = FabricMenuItemRenderer.toMinecraft(this.renderer.title(
                this.selected.title(), this.renderer.menuPlaceholders(page, pages, target)
        ));
        this.sessions.open(viewer, title, container, page, List.of(), target, (slot, leftClick) -> {
            final List<Integer> contentSlots = contentSlots(this.selected.allowedSlots(), this.selected.items());
            if (contentSlots.contains(slot)) {
                this.actions.execute(viewer, leftClick
                                ? this.selected.entryItem().leftClickActions()
                                : this.selected.entryItem().rightClickActions(),
                        this.renderer.menuPlaceholders(page, pages, target), target, page);
            } else {
                clickConfiguredItem(viewer, slot, page, pages, target, leftClick);
            }
        });

    }

    private void renderStatic(
            final @NotNull SimpleContainer container,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders
    ) {

        for (final ItemConfiguration configured : items.values()) {
            final MenuItemData rendered = this.renderer.staticItem(configured, placeholders);
            for (final int slot : configured.slots()) {
                container.setItem(slot, FabricMenuItemRenderer.item(rendered));
            }
        }

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

    private void clickConfiguredItem(
            final @NotNull ServerPlayer viewer,
            final int slot,
            final int page,
            final int pages,
            final @Nullable TrackedPlayer target,
            final boolean leftClick
    ) {

        final Map<String, ItemConfiguration> items = target == null ? this.selector.items() : this.selected.items();
        final Map<String, String> placeholders = this.renderer.menuPlaceholders(page, pages, target);
        for (final ItemConfiguration item : items.values()) {
            if (!item.slots().contains(slot)) {
                continue;
            }
            this.actions.execute(viewer, leftClick ? item.leftClickActions() : item.rightClickActions(),
                    placeholders, target, page);
        }

    }

    private static long latestFailure(final @NotNull TrackedPlayer player) {

        final List<HistoryEntry> entries = player.history();
        return entries.isEmpty() ? Long.MIN_VALUE : entries.get(entries.size() - 1).timestamp();

    }

    public void refreshSessions() {

        for (final FabricMenuSessionManager.Session session : this.sessions.snapshot()) {
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
        for (final FabricMenuSessionManager.Session session : this.sessions.snapshot()) {
            this.environment.scheduler().<ServerPlayer>mapEntity(
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


}

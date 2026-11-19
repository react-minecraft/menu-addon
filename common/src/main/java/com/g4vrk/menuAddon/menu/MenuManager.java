package com.g4vrk.menuAddon.menu;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.menuAddon.config.AddonSettings;
import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.menuAddon.util.InventoryViewCompat;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.player.registry.TrackedPlayerRegistry;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MenuManager implements MenuActionExecutor.Navigator {

    private final TrackedPlayerRegistry playerRegistry;
    private final TextFormatter text;
    private final MenuActionExecutor actionExecutor;
    private final Scheduler scheduler;

    private volatile State state;

    public MenuManager(
            final @NotNull TrackedPlayerRegistry playerRegistry,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull TextFormatter text,
            final @NotNull Logger logger,
            final @NotNull Scheduler scheduler,
            final @NotNull AddonSettings settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {
        this.playerRegistry = playerRegistry;
        this.text = text;
        this.scheduler = scheduler;
        this.actionExecutor = new MenuActionExecutor(
                playerRegistry,
                actionParser,
                text,
                logger,
                scheduler,
                this
        );
        this.apply(settings, targetSelectorConfig, selectedTargetConfig);
    }

    public boolean canView(
            final @NotNull Player player
    ) {
        return this.canView(player, this.state);
    }

    public @NotNull String viewPermission() {
        return this.state.settings().permission();
    }

    @Override
    public void openTargetSelector(
            final @NotNull Player viewer,
            final int requestedPage
    ) {
        final State state = this.state;

        if (!this.canView(viewer, state)) {
            return;
        }

        final List<TrackedPlayer> targets = this.targets(state);
        final List<Integer> contentSlots = contentSlots(
                state.targetSelectorConfig().allowedSlots(),
                state.targetSelectorConfig().items()
        );
        final int totalPages = pages(targets.size(), contentSlots.size());
        final int page = clampPage(requestedPage, totalPages);
        final Map<String, String> placeholders = state.renderer().menuPlaceholders(page, totalPages, null);
        final MenuHolder holder = new MenuHolder(
                MenuHolder.Type.TARGET_SELECTOR,
                page,
                totalPages,
                null
        );
        final Inventory inventory = Bukkit.createInventory(
                holder,
                state.targetSelectorConfig().size(),
                state.renderer().title(state.targetSelectorConfig().title(), placeholders)
        );

        holder.attach(inventory);
        this.renderTargetSelector(holder, viewer, state);
        viewer.openInventory(inventory);
        this.startRefresh(viewer, holder);
    }

    @Override
    public void openSelectedTarget(
            final @NotNull Player viewer,
            final @NotNull TrackedPlayer target,
            final int requestedPage
    ) {
        final State state = this.state;

        if (!this.canView(viewer, state) || this.playerRegistry.get(target.uniqueId()) != target) {
            return;
        }

        final int groupCount = historyGroups(
                target,
                state.selectedTargetConfig().entryItem().failuresPerEntry()
        ).size();
        final List<Integer> contentSlots = contentSlots(
                state.selectedTargetConfig().allowedSlots(),
                state.selectedTargetConfig().items()
        );
        final int totalPages = pages(groupCount, contentSlots.size());
        final int page = clampPage(requestedPage, totalPages);
        final Map<String, String> placeholders = state.renderer().menuPlaceholders(page, totalPages, target);
        final MenuHolder holder = new MenuHolder(
                MenuHolder.Type.SELECTED_TARGET,
                page,
                totalPages,
                target.uniqueId()
        );
        final Inventory inventory = Bukkit.createInventory(
                holder,
                state.selectedTargetConfig().size(),
                state.renderer().title(state.selectedTargetConfig().title(), placeholders)
        );

        holder.attach(inventory);
        this.renderSelectedTarget(holder, viewer, target, state);
        viewer.openInventory(inventory);
        this.startRefresh(viewer, holder);
    }

    @Override
    public void switchPage(
            final @NotNull MenuActionExecutor.Context context,
            final int page
    ) {
        if (context.menuType() == MenuHolder.Type.TARGET_SELECTOR) {
            this.openTargetSelector(context.viewer(), page);
        } else if (context.target() != null) {
            this.openSelectedTarget(context.viewer(), context.target(), page);
        }
    }

    public void refresh(
            final @NotNull TrackedPlayer changedTarget
    ) {
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            this.scheduleForViewer(viewer, () -> this.refresh(viewer, changedTarget));
        }
    }

    public void reload(
            final @NotNull AddonSettings settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {
        this.apply(settings, targetSelectorConfig, selectedTargetConfig);

        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            this.scheduleForViewer(viewer, () -> this.refreshOpenMenu(viewer));
        }
    }

    public static void validate(
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {

        contentSlots(targetSelectorConfig.allowedSlots(), targetSelectorConfig.items());
        contentSlots(selectedTargetConfig.allowedSlots(), selectedTargetConfig.items());

    }

    public void closeAll() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.scheduleForViewer(player, () -> this.closeMenu(player));
        }
    }

    private void apply(
            final @NotNull AddonSettings settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {
        validate(targetSelectorConfig, selectedTargetConfig);

        this.state = new State(
                settings,
                targetSelectorConfig,
                selectedTargetConfig,
                new MenuTextRenderer(this.text, settings)
        );
    }

    private void renderTargetSelector(
            final @NotNull MenuHolder holder,
            final @NotNull Player viewer,
            final @NotNull State state
    ) {
        final List<TrackedPlayer> targets = this.targets(state);
        final List<Integer> slots = contentSlots(
                state.targetSelectorConfig().allowedSlots(),
                state.targetSelectorConfig().items()
        );
        final int totalPages = pages(targets.size(), slots.size());
        final int page = clampPage(holder.page(), totalPages);
        final Map<String, String> menuPlaceholders = state.renderer().menuPlaceholders(page, totalPages, null);

        holder.reset(page, totalPages);
        this.renderStaticItems(
                holder,
                state.targetSelectorConfig().items(),
                menuPlaceholders,
                null,
                state
        );

        final int offset = page * slots.size();
        final int amount = Math.min(slots.size(), targets.size() - offset);

        for (int index = 0; index < amount; index++) {
            final int slot = slots.get(index);
            final TrackedPlayer target = targets.get(offset + index);
            final List<HistoryEntry> history = target.history();
            holder.getInventory().setItem(
                    slot,
                    state.renderer().targetItem(state.targetSelectorConfig().targetItem(), target, history)
            );
            holder.addTimeUpdate(() -> holder.getInventory().setItem(
                    slot,
                    state.renderer().targetItem(state.targetSelectorConfig().targetItem(), target, history)
            ));
            this.bind(
                    holder,
                    slot,
                    state.targetSelectorConfig().targetItem().leftClickActions(),
                    state.targetSelectorConfig().targetItem().rightClickActions(),
                    target,
                    state.renderer().menuPlaceholders(page, totalPages, target)
            );
        }
    }

    private void renderSelectedTarget(
            final @NotNull MenuHolder holder,
            final @NotNull Player viewer,
            final @NotNull TrackedPlayer target,
            final @NotNull State state
    ) {
        final List<List<HistoryEntry>> groups = historyGroups(
                target,
                state.selectedTargetConfig().entryItem().failuresPerEntry()
        );
        final List<Integer> slots = contentSlots(
                state.selectedTargetConfig().allowedSlots(),
                state.selectedTargetConfig().items()
        );
        final int totalPages = pages(groups.size(), slots.size());
        final int page = clampPage(holder.page(), totalPages);
        final Map<String, String> menuPlaceholders = state.renderer().menuPlaceholders(page, totalPages, target);

        holder.reset(page, totalPages);
        this.renderStaticItems(
                holder,
                state.selectedTargetConfig().items(),
                menuPlaceholders,
                target,
                state
        );

        final int offset = page * slots.size();
        final int amount = Math.min(slots.size(), groups.size() - offset);

        for (int index = 0; index < amount; index++) {
            final int slot = slots.get(index);
            final List<HistoryEntry> entries = groups.get(offset + index);
            holder.getInventory().setItem(
                    slot,
                    state.renderer().historyGroupItem(
                            state.selectedTargetConfig().entryItem(),
                            target,
                            entries,
                            menuPlaceholders
                    )
            );
            holder.addTimeUpdate(() -> holder.getInventory().setItem(
                    slot,
                    state.renderer().historyGroupItem(state.selectedTargetConfig().entryItem(), target, entries, menuPlaceholders)
            ));
            this.bind(
                    holder,
                    slot,
                    state.selectedTargetConfig().entryItem().leftClickActions(),
                    state.selectedTargetConfig().entryItem().rightClickActions(),
                    target,
                    menuPlaceholders
            );
        }
    }

    private void renderStaticItems(
            final @NotNull MenuHolder holder,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders,
            final TrackedPlayer target,
            final @NotNull State state
    ) {
        for (final ItemConfiguration item : items.values()) {
            for (final int slot : item.slots()) {
                holder.getInventory().setItem(slot, state.renderer().staticItem(item, placeholders));
                this.bind(
                        holder,
                        slot,
                        item.leftClickActions(),
                        item.rightClickActions(),
                        target,
                        placeholders
                );
            }
        }
    }

    private void bind(
            final @NotNull MenuHolder holder,
            final int slot,
            final @NotNull List<String> leftClickActions,
            final @NotNull List<String> rightClickActions,
            final TrackedPlayer target,
            final @NotNull Map<String, String> placeholders
    ) {
        holder.bind(slot, (viewer, clickType) -> {
            if (!this.canView(viewer)) {
                viewer.closeInventory();
                return;
            }
            final List<String> actions = clickActions(
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

    private @NotNull List<TrackedPlayer> targets(
            final @NotNull State state
    ) {
        final List<TrackedPlayer> targets = new ArrayList<>();

        for (final TrackedPlayer target : this.playerRegistry) {
            if (!state.settings().includeEmptyHistory() && target.history().isEmpty()) {
                continue;
            }

            targets.add(target);
        }

        Comparator<TrackedPlayer> comparator = switch (state.targetSelectorConfig().sortBy()) {
            case LATEST_FAILURE -> Comparator.comparingLong(MenuManager::latestTimestamp);
            case AVERAGE_PROBABILITY -> Comparator.comparingDouble(
                    MenuTextRenderer::averageProbability
            );
            case NAME -> Comparator.comparing(TrackedPlayer::name, String.CASE_INSENSITIVE_ORDER);
        };

        if (state.targetSelectorConfig().descending()) {
            comparator = comparator.reversed();
        }

        targets.sort(comparator.thenComparing(TrackedPlayer::name, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    private void refresh(
            final @NotNull Player viewer,
            final @NotNull TrackedPlayer changedTarget
    ) {
        final State state = this.state;

        if (!(InventoryViewCompat.getTopInventory(viewer).getHolder() instanceof MenuHolder holder)) {
            return;
        }

        final int configuredSize = holder.type() == MenuHolder.Type.TARGET_SELECTOR
                ? state.targetSelectorConfig().size() : state.selectedTargetConfig().size();
        if (holder.getInventory().getSize() != configuredSize) {
            this.refreshOpenMenu(viewer);
            return;
        }

        if (holder.type() == MenuHolder.Type.TARGET_SELECTOR
                && state.targetSelectorConfig().targetItem().updateOnFailure()) {
            this.renderTargetSelector(holder, viewer, state);
            return;
        }

        if (holder.type() == MenuHolder.Type.SELECTED_TARGET
                && changedTarget.uniqueId().equals(holder.targetId())
                && state.selectedTargetConfig().entryItem().updateOnFailure()) {
            this.renderSelectedTarget(holder, viewer, changedTarget, state);
        }
    }

    private void refreshOpenMenu(
            final @NotNull Player viewer
    ) {
        final State state = this.state;

        if (!(InventoryViewCompat.getTopInventory(viewer).getHolder() instanceof MenuHolder holder)) {
            return;
        }

        if (!this.canView(viewer, state)) {
            viewer.closeInventory();
            return;
        }

        if (holder.type() == MenuHolder.Type.TARGET_SELECTOR) {
            this.openTargetSelector(viewer, holder.page());
            return;
        }

        final UUID targetId = holder.targetId();
        final TrackedPlayer target = targetId == null ? null : this.playerRegistry.get(targetId);

        if (target == null) {
            this.openTargetSelector(viewer, 0);
        } else {
            this.openSelectedTarget(viewer, target, holder.page());
        }
    }

    private void closeMenu(
            final @NotNull Player player
    ) {
        if (InventoryViewCompat.getTopInventory(player).getHolder() instanceof MenuHolder) {
            player.closeInventory();
        }
    }

    private void startRefresh(final @NotNull Player viewer, final @NotNull MenuHolder holder) {

        holder.refreshTask(this.scheduler.scheduleEntity(viewer, task -> {
            if (InventoryViewCompat.getTopInventory(viewer).getHolder() != holder) {
                task.cancel();
                return;
            }
            final State state = this.state;
            if (!this.canView(viewer, state)) {
                viewer.closeInventory();
                task.cancel();
                return;
            }
            final int configuredSize = holder.type() == MenuHolder.Type.TARGET_SELECTOR
                    ? state.targetSelectorConfig().size() : state.selectedTargetConfig().size();
            if (holder.getInventory().getSize() != configuredSize) {
                this.refreshOpenMenu(viewer);
                task.cancel();
                return;
            }
            if (holder.type() == MenuHolder.Type.SELECTED_TARGET) {
                final TrackedPlayer target = this.playerRegistry.get(holder.targetId());
                if (target == null) {
                    this.openTargetSelector(viewer, 0);
                    task.cancel();
                    return;
                }
            }
            holder.updateTime();
        }, TickSchedule.repeating(20, 20)));

    }

    private void scheduleForViewer(
            final @NotNull Player viewer,
            final @NotNull Runnable action
    ) {
        this.scheduler.scheduleEntity(viewer, action, TickSchedule.instant());
    }

    private boolean canView(
            final @NotNull Player player,
            final @NotNull State state
    ) {
        final String permission = state.settings().permission();
        return permission.isEmpty() || player.hasPermission(permission);
    }

    private static @NotNull List<List<HistoryEntry>> historyGroups(
            final @NotNull TrackedPlayer target,
            final int groupSize
    ) {
        final List<HistoryEntry> history = target.history();
        final List<HistoryEntry> newestFirst = new ArrayList<>(history.size());

        for (int index = history.size() - 1; index >= 0; index--) {
            newestFirst.add(history.get(index));
        }

        final List<List<HistoryEntry>> groups = new ArrayList<>();

        for (int start = 0; start < newestFirst.size(); start += groupSize) {
            groups.add(List.copyOf(
                    newestFirst.subList(start, Math.min(newestFirst.size(), start + groupSize))
            ));
        }

        return groups;
    }

    private static @NotNull List<Integer> contentSlots(
            final @NotNull List<Integer> configuredSlots,
            final @NotNull Map<String, ItemConfiguration> items
    ) {
        final Set<Integer> reserved = new HashSet<>();

        for (final ItemConfiguration item : items.values()) {
            reserved.addAll(item.slots());
        }

        final List<Integer> result = configuredSlots.stream()
                .filter(slot -> !reserved.contains(slot))
                .toList();

        if (result.isEmpty()) {
            throw new IllegalArgumentException("Menu has no content slots after static item slots are reserved");
        }

        return result;
    }

    private static @NotNull List<String> clickActions(
            final @NotNull ClickType clickType,
            final @NotNull List<String> leftClickActions,
            final @NotNull List<String> rightClickActions
    ) {
        if (clickType.isLeftClick()) {
            return leftClickActions;
        }

        if (clickType.isRightClick()) {
            return rightClickActions;
        }

        return List.of();
    }

    private static int pages(
            final int itemCount,
            final int pageSize
    ) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    private static int clampPage(
            final int page,
            final int totalPages
    ) {
        return Math.max(0, Math.min(page, totalPages - 1));
    }

    private static long latestTimestamp(
            final @NotNull TrackedPlayer target
    ) {
        final List<HistoryEntry> history = target.history();
        return history.isEmpty() ? 0L : history.get(history.size() - 1).timestamp();
    }

    /**
     * Keeps one immutable configuration set visible to concurrent entity scheduler tasks.
     */
    private record State(
            @NotNull AddonSettings settings,
            @NotNull TargetSelectorConfiguration targetSelectorConfig,
            @NotNull SelectedTargetConfiguration selectedTargetConfig,
            @NotNull MenuTextRenderer renderer
    ) {
    }

}

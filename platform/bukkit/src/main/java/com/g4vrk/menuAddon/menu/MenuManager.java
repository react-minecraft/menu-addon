package com.g4vrk.menuAddon.menu;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.menuAddon.config.AddonConfig;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.menu.action.MenuActionTable;
import com.g4vrk.menuAddon.menu.bukkit.BukkitMenuRenderer;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.menuAddon.util.InventoryViewCompat;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.player.registry.TrackedPlayerRegistry;
import com.g4vrk.react.api.text.TextFormatter;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates Bukkit menu opening, template reloads, and live history refreshes.
 */
public final class MenuManager implements MenuActionExecutor.Navigator {

    private final TrackedPlayerRegistry playerRegistry;
    private final TextFormatter text;
    private final MenuActionExecutor actionExecutor;
    private final TaskScheduler<Player> scheduler;
    private final MenuSessionRegistry sessions;
    private final BukkitMenuRenderer menuRenderer;

    private volatile State state;

    public MenuManager(
            final @NotNull TrackedPlayerRegistry playerRegistry,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull TextFormatter text,
            final @NotNull Logger logger,
            final @NotNull TaskScheduler<Player> scheduler,
            final @NotNull AddonConfig settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {

        this.playerRegistry = playerRegistry;
        this.text = text;
        this.scheduler = scheduler;
        this.sessions = new MenuSessionRegistry(scheduler);
        this.actionExecutor = new MenuActionExecutor(
                playerRegistry,
                actionParser,
                text,
                logger,
                scheduler,
                this
        );
        this.menuRenderer = new BukkitMenuRenderer(this.actionExecutor, this::canView);
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

        final List<TrackedPlayer> targets = MenuLayout.targets(
                this.playerRegistry,
                state.settings().includeEmptyHistory(),
                state.targetSelectorConfig()
        );
        final List<Integer> contentSlots = MenuLayout.contentSlots(
                state.targetSelectorConfig().allowedSlots(),
                state.targetSelectorConfig().items()
        );
        final int totalPages = MenuLayout.pages(targets.size(), contentSlots.size());
        final int page = MenuLayout.clampPage(requestedPage, totalPages);
        final Map<String, String> placeholders = state.renderer().menuPlaceholders(page, totalPages, null);
        final MenuHolder holder = new MenuHolder(
                MenuType.TARGET_SELECTOR,
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
        this.menuRenderer.renderTargetSelector(
                holder,
                state.targetSelectorConfig(),
                state.renderer(),
                state.actions(),
                targets
        );
        this.sessions.openInventory(viewer, holder, inventory, this::refreshTick);

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

        final List<List<HistoryEntry>> groups = MenuLayout.historyGroups(
                target,
                state.selectedTargetConfig().entryItem().failuresPerEntry()
        );
        final List<Integer> contentSlots = MenuLayout.contentSlots(
                state.selectedTargetConfig().allowedSlots(),
                state.selectedTargetConfig().items()
        );
        final int totalPages = MenuLayout.pages(groups.size(), contentSlots.size());
        final int page = MenuLayout.clampPage(requestedPage, totalPages);
        final Map<String, String> placeholders = state.renderer().menuPlaceholders(page, totalPages, target);
        final MenuHolder holder = new MenuHolder(
                MenuType.SELECTED_TARGET,
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
        this.menuRenderer.renderSelectedTarget(
                holder,
                state.selectedTargetConfig(),
                state.renderer(),
                state.actions(),
                target,
                groups
        );
        this.sessions.openInventory(viewer, holder, inventory, this::refreshTick);

    }

    @Override
    public void switchPage(
            final @NotNull MenuActionExecutor.Context context,
            final int page
    ) {

        if (context.menuType() == MenuType.TARGET_SELECTOR) {
            this.openTargetSelector(context.viewer(), page);
        } else if (context.target() != null) {
            this.openSelectedTarget(context.viewer(), context.target(), page);
        }

    }

    public void refresh(
            final @NotNull TrackedPlayer changedTarget
    ) {

        for (final Player viewer : this.sessions.openViewers()) {
            this.scheduler.scheduleEntity(
                    viewer,
                    () -> this.refreshViewer(viewer, changedTarget),
                    TickSchedule.instant()
            );
        }

    }

    public void reload(
            final @NotNull AddonConfig settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {

        this.apply(settings, targetSelectorConfig, selectedTargetConfig);

        for (final Player viewer : this.sessions.openViewers()) {
            this.scheduler.scheduleEntity(
                    viewer,
                    () -> this.refreshOpenMenu(viewer),
                    TickSchedule.instant()
            );
        }

    }

    public static void validate(
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {

        MenuLayout.validate(targetSelectorConfig, selectedTargetConfig);

    }

    public @NotNull CompletableFuture<Void> closeAll() {

        return this.sessions.closeAll();

    }

    public void closeImmediately() {

        this.sessions.closeImmediately();

    }

    public void cancelClose() {

        this.sessions.cancelClose();

    }

    public void menuClosed(
            final @NotNull Player viewer,
            final @NotNull MenuHolder holder
    ) {

        this.sessions.menuClosed(viewer, holder);

    }

    public void viewerClosed(
            final @NotNull UUID playerId
    ) {

        this.sessions.viewerClosed(playerId);

    }

    private void apply(
            final @NotNull AddonConfig settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {

        MenuLayout.validate(targetSelectorConfig, selectedTargetConfig);

        this.state = new State(
                settings,
                targetSelectorConfig,
                selectedTargetConfig,
                new MenuTextRenderer(this.text, settings),
                this.actionExecutor.compileTable(targetSelectorConfig, selectedTargetConfig)
        );

    }

    private void refreshViewer(
            final @NotNull Player viewer,
            final @NotNull TrackedPlayer changedTarget
    ) {

        final State state = this.state;

        if (!(InventoryViewCompat.getTopInventory(viewer).getHolder() instanceof MenuHolder holder)) {
            return;
        }

        final int configuredSize = holder.type() == MenuType.TARGET_SELECTOR
                ? state.targetSelectorConfig().size() : state.selectedTargetConfig().size();
        if (holder.getInventory().getSize() != configuredSize) {
            this.refreshOpenMenu(viewer);
            return;
        }

        if (holder.type() == MenuType.TARGET_SELECTOR
                && state.targetSelectorConfig().targetItem().updateOnFailure()) {
            final List<TrackedPlayer> targets = MenuLayout.targets(
                    this.playerRegistry,
                    state.settings().includeEmptyHistory(),
                    state.targetSelectorConfig()
            );
            this.menuRenderer.renderTargetSelector(
                    holder,
                    state.targetSelectorConfig(),
                    state.renderer(),
                    state.actions(),
                    targets
            );
            return;
        }

        if (holder.type() == MenuType.SELECTED_TARGET
                && changedTarget.uniqueId().equals(holder.targetId())
                && state.selectedTargetConfig().entryItem().updateOnFailure()) {
            final List<List<HistoryEntry>> groups = MenuLayout.historyGroups(
                    changedTarget,
                    state.selectedTargetConfig().entryItem().failuresPerEntry()
            );
            this.menuRenderer.renderSelectedTarget(
                    holder,
                    state.selectedTargetConfig(),
                    state.renderer(),
                    state.actions(),
                    changedTarget,
                    groups
            );
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

        if (holder.type() == MenuType.TARGET_SELECTOR) {
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

    private boolean refreshTick(
            final @NotNull Player viewer,
            final @NotNull MenuHolder holder
    ) {

        final State state = this.state;

        if (!this.canView(viewer, state)) {
            viewer.closeInventory();
            return false;
        }

        final int configuredSize = holder.type() == MenuType.TARGET_SELECTOR
                ? state.targetSelectorConfig().size() : state.selectedTargetConfig().size();
        if (holder.getInventory().getSize() != configuredSize) {
            this.refreshOpenMenu(viewer);
            return false;
        }

        if (holder.type() == MenuType.SELECTED_TARGET) {
            final UUID targetId = holder.targetId();
            final TrackedPlayer target = targetId == null ? null : this.playerRegistry.get(targetId);
            if (target == null) {
                this.openTargetSelector(viewer, 0);
                return false;
            }
        }

        return true;

    }

    private boolean canView(
            final @NotNull Player player,
            final @NotNull State state
    ) {

        final String permission = state.settings().permission();
        return permission.isEmpty() || player.hasPermission(permission);

    }

    /**
     * Keeps one immutable configuration set visible to concurrent entity scheduler tasks.
     */
    private record State(
            @NotNull AddonConfig settings,
            @NotNull TargetSelectorConfiguration targetSelectorConfig,
            @NotNull SelectedTargetConfiguration selectedTargetConfig,
            @NotNull MenuTextRenderer renderer,
            @NotNull MenuActionTable<Player> actions
    ) {
    }

}

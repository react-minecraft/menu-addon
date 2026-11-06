package com.g4vrk.menuAddon.menu;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.menuAddon.config.AddonSettings;
import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.menuAddon.util.InventoryViewCompat;
import com.g4vrk.react.React;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.registry.PlayerRegistry;
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

    private final PlayerRegistry playerRegistry;
    private final MenuActionExecutor actionExecutor;

    private AddonSettings settings;
    private TargetSelectorConfiguration targetSelectorConfig;
    private SelectedTargetConfiguration selectedTargetConfig;
    private MenuTextRenderer renderer;

    public MenuManager(
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull Logger logger,
            final @NotNull AddonSettings settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {
        this.playerRegistry = playerRegistry;
        this.actionExecutor = new MenuActionExecutor(actionParser, React.INSTANCE.getSerializers().universalSerializer(), logger, this);
        this.apply(settings, targetSelectorConfig, selectedTargetConfig);
    }

    public boolean canView(
            final @NotNull Player player
    ) {
        return this.settings.permission().isEmpty() || player.hasPermission(this.settings.permission());
    }

    public @NotNull String viewPermission() {
        return this.settings.permission();
    }

    @Override
    public void openTargetSelector(
            final @NotNull Player viewer,
            final int requestedPage
    ) {
        if (!this.canView(viewer)) {
            return;
        }

        final List<ReactPlayer> targets = this.targets();
        final List<Integer> contentSlots = contentSlots(
                this.targetSelectorConfig.allowedSlots(),
                this.targetSelectorConfig.items()
        );
        final int totalPages = pages(targets.size(), contentSlots.size());
        final int page = clampPage(requestedPage, totalPages);
        final Map<String, String> placeholders = this.renderer.menuPlaceholders(page, totalPages, null);
        final MenuHolder holder = new MenuHolder(
                MenuHolder.Type.TARGET_SELECTOR,
                page,
                totalPages,
                null
        );
        final Inventory inventory = Bukkit.createInventory(
                holder,
                this.targetSelectorConfig.size(),
                this.renderer.title(this.targetSelectorConfig.title(), placeholders)
        );

        holder.attach(inventory);
        this.renderTargetSelector(holder, viewer);
        viewer.openInventory(inventory);
    }

    @Override
    public void openSelectedTarget(
            final @NotNull Player viewer,
            final @NotNull ReactPlayer target,
            final int requestedPage
    ) {
        if (!this.canView(viewer) || !target.bukkitPlayer.isOnline()) {
            return;
        }

        final int groupCount = historyGroups(target, this.selectedTargetConfig.entryItem().failuresPerEntry()).size();
        final List<Integer> contentSlots = contentSlots(
                this.selectedTargetConfig.allowedSlots(),
                this.selectedTargetConfig.items()
        );
        final int totalPages = pages(groupCount, contentSlots.size());
        final int page = clampPage(requestedPage, totalPages);
        final Map<String, String> placeholders = this.renderer.menuPlaceholders(page, totalPages, target);
        final MenuHolder holder = new MenuHolder(
                MenuHolder.Type.SELECTED_TARGET,
                page,
                totalPages,
                target.getUniqueId()
        );
        final Inventory inventory = Bukkit.createInventory(
                holder,
                this.selectedTargetConfig.size(),
                this.renderer.title(this.selectedTargetConfig.title(), placeholders)
        );

        holder.attach(inventory);
        this.renderSelectedTarget(holder, viewer, target);
        viewer.openInventory(inventory);
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
            final @NotNull ReactPlayer changedTarget
    ) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Open menus may only be refreshed on the server thread");
        }

        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            if (!(InventoryViewCompat.getTopInventory(viewer).getHolder() instanceof MenuHolder holder)) {
                continue;
            }

            if (holder.type() == MenuHolder.Type.TARGET_SELECTOR
                    && this.targetSelectorConfig.targetItem().updateOnFailure()) {
                this.renderTargetSelector(holder, viewer);
                continue;
            }

            if (holder.type() == MenuHolder.Type.SELECTED_TARGET
                    && changedTarget.getUniqueId().equals(holder.targetId())
                    && this.selectedTargetConfig.entryItem().updateOnFailure()) {
                this.renderSelectedTarget(holder, viewer, changedTarget);
            }
        }
    }

    public void reload(
            final @NotNull AddonSettings settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {
        final List<OpenMenu> openMenus = this.openMenus();
        this.apply(settings, targetSelectorConfig, selectedTargetConfig);

        for (final OpenMenu openMenu : openMenus) {
            if (!this.canView(openMenu.viewer())) {
                openMenu.viewer().closeInventory();
                continue;
            }

            if (openMenu.type() == MenuHolder.Type.TARGET_SELECTOR) {
                this.openTargetSelector(openMenu.viewer(), openMenu.page());
                continue;
            }

            final ReactPlayer target = this.playerRegistry.getPlayer(openMenu.targetId());

            if (target == null) {
                this.openTargetSelector(openMenu.viewer(), 0);
            } else {
                this.openSelectedTarget(openMenu.viewer(), target, openMenu.page());
            }
        }
    }

    public void closeAll() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (InventoryViewCompat.getTopInventory(player).getHolder() instanceof MenuHolder) {
                player.closeInventory();
            }
        }
    }

    private void apply(
            final @NotNull AddonSettings settings,
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {
        contentSlots(targetSelectorConfig.allowedSlots(), targetSelectorConfig.items());
        contentSlots(selectedTargetConfig.allowedSlots(), selectedTargetConfig.items());

        this.settings = settings;
        this.targetSelectorConfig = targetSelectorConfig;
        this.selectedTargetConfig = selectedTargetConfig;
        this.renderer = new MenuTextRenderer(React.INSTANCE.getSerializers().universalSerializer(), settings);
    }

    private void renderTargetSelector(
            final @NotNull MenuHolder holder,
            final @NotNull Player viewer
    ) {
        final List<ReactPlayer> targets = this.targets();
        final List<Integer> slots = contentSlots(
                this.targetSelectorConfig.allowedSlots(),
                this.targetSelectorConfig.items()
        );
        final int totalPages = pages(targets.size(), slots.size());
        final int page = clampPage(holder.page(), totalPages);
        final Map<String, String> menuPlaceholders = this.renderer.menuPlaceholders(page, totalPages, null);

        holder.reset(page, totalPages);
        this.renderStaticItems(
                holder,
                this.targetSelectorConfig.items(),
                menuPlaceholders,
                null
        );

        final int offset = page * slots.size();
        final int amount = Math.min(slots.size(), targets.size() - offset);

        for (int index = 0; index < amount; index++) {
            final int slot = slots.get(index);
            final ReactPlayer target = targets.get(offset + index);
            holder.getInventory().setItem(
                    slot,
                    this.renderer.targetItem(this.targetSelectorConfig.targetItem(), target)
            );
            this.bind(
                    holder,
                    slot,
                    this.targetSelectorConfig.targetItem().leftClickActions(),
                    this.targetSelectorConfig.targetItem().rightClickActions(),
                    target,
                    this.renderer.menuPlaceholders(page, totalPages, target)
            );
        }
    }

    private void renderSelectedTarget(
            final @NotNull MenuHolder holder,
            final @NotNull Player viewer,
            final @NotNull ReactPlayer target
    ) {
        final List<List<InferenceHistoryEntry>> groups = historyGroups(
                target,
                this.selectedTargetConfig.entryItem().failuresPerEntry()
        );
        final List<Integer> slots = contentSlots(
                this.selectedTargetConfig.allowedSlots(),
                this.selectedTargetConfig.items()
        );
        final int totalPages = pages(groups.size(), slots.size());
        final int page = clampPage(holder.page(), totalPages);
        final Map<String, String> menuPlaceholders = this.renderer.menuPlaceholders(page, totalPages, target);

        holder.reset(page, totalPages);
        this.renderStaticItems(
                holder,
                this.selectedTargetConfig.items(),
                menuPlaceholders,
                target
        );

        final int offset = page * slots.size();
        final int amount = Math.min(slots.size(), groups.size() - offset);

        for (int index = 0; index < amount; index++) {
            final int slot = slots.get(index);
            holder.getInventory().setItem(
                    slot,
                    this.renderer.historyGroupItem(
                            this.selectedTargetConfig.entryItem(),
                            target,
                            groups.get(offset + index),
                            menuPlaceholders
                    )
            );
            this.bind(
                    holder,
                    slot,
                    this.selectedTargetConfig.entryItem().leftClickActions(),
                    this.selectedTargetConfig.entryItem().rightClickActions(),
                    target,
                    menuPlaceholders
            );
        }
    }

    private void renderStaticItems(
            final @NotNull MenuHolder holder,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders,
            final ReactPlayer target
    ) {
        for (final ItemConfiguration item : items.values()) {
            for (final int slot : item.slots()) {
                holder.getInventory().setItem(slot, this.renderer.staticItem(item, placeholders));
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
            final ReactPlayer target,
            final @NotNull Map<String, String> placeholders
    ) {
        holder.bind(slot, (viewer, clickType) -> {
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

    private @NotNull List<ReactPlayer> targets() {
        final List<ReactPlayer> targets = new ArrayList<>();

        for (final ReactPlayer target : this.playerRegistry.all()) {
            if (this.targetSelectorConfig.onlyOnline() && !target.bukkitPlayer.isOnline()) {
                continue;
            }

            if (!this.settings.includeEmptyHistory() && target.inferenceHistory.size() == 0) {
                continue;
            }

            targets.add(target);
        }

        Comparator<ReactPlayer> comparator = switch (this.targetSelectorConfig.sortBy()) {
            case LATEST_FAILURE -> Comparator.comparingLong(MenuManager::latestTimestamp);
            case AVERAGE_PROBABILITY -> Comparator.comparingDouble(
                    target -> target.inferenceStatistic.averageProbability()
            );
            case NAME -> Comparator.comparing(ReactPlayer::getName, String.CASE_INSENSITIVE_ORDER);
        };

        if (this.targetSelectorConfig.descending()) {
            comparator = comparator.reversed();
        }

        targets.sort(comparator.thenComparing(ReactPlayer::getName, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    private @NotNull List<OpenMenu> openMenus() {
        final List<OpenMenu> result = new ArrayList<>();

        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!(InventoryViewCompat.getTopInventory(player).getHolder() instanceof MenuHolder holder)) {
                continue;
            }

            result.add(new OpenMenu(
                    player,
                    holder.type(),
                    holder.page(),
                    holder.targetId()
            ));
        }

        return result;
    }

    private static @NotNull List<List<InferenceHistoryEntry>> historyGroups(
            final @NotNull ReactPlayer target,
            final int groupSize
    ) {
        final InferenceHistoryEntry[] history = target.inferenceHistory.entries();
        final List<InferenceHistoryEntry> newestFirst = new ArrayList<>(history.length);

        for (int index = history.length - 1; index >= 0; index--) {
            newestFirst.add(history[index]);
        }

        final List<List<InferenceHistoryEntry>> groups = new ArrayList<>();

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
            final @NotNull ReactPlayer target
    ) {
        final InferenceHistoryEntry latest = target.inferenceHistory.latest();
        return latest == null ? 0L : latest.getTimestamp();
    }

    private record OpenMenu(
            @NotNull Player viewer,
            @NotNull MenuHolder.Type type,
            int page,
            UUID targetId
    ) {
    }
}

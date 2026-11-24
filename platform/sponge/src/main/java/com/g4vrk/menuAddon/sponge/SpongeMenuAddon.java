package com.g4vrk.menuAddon.sponge;

import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.menuAddon.config.AddonConfig;
import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.sponge.action.SpongePlayerAction;
import com.g4vrk.menuAddon.sponge.menu.SpongeMenuRenderer;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.menu.ClickTypes;
import org.spongepowered.api.item.inventory.menu.InventoryMenu;
import org.spongepowered.api.item.inventory.type.ViewableInventory;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.concurrent.CompletableFuture;

/**
 * Sponge-native Menu addon runtime.
 */
public final class SpongeMenuAddon extends JavaAddon {

    private final Map<UUID, Session> sessions = new HashMap<>();
    private AddonEnvironment environment;
    private String commandLiteral;
    private String commandPermission;
    private boolean includeEmptyHistory;
    private TargetSelectorConfiguration selector;
    private SelectedTargetConfiguration selected;
    private MenuTextRenderer renderer;
    private volatile boolean disabled;
    private volatile CompletableFuture<Void> pendingReload;

    @Override
    public void onEnable() {

        this.disabled = false;
        this.environment = environment();
        loadSettings();
        this.environment.listeners().register(new InferenceRefreshListener());
        final CommandRegistrar commands = this.environment.commands();
        commands.register("react", commands.command("react")
                .literal(this.commandLiteral)
                .permission(this.commandPermission)
                .handler(context -> {
                    final PlatformPlayer sender = context.sender().asPlayer();
                    final ServerPlayer player = sender == null ? null : sender.unwrap(ServerPlayer.class);
                    if (player == null) {
                        context.sender().sendMessage(Component.text("This command can only be used by a player."));
                        return;
                    }
                    openPlayers(player, 0);
                }));
        commands.register("react", commands.command("react")
                .literal(this.commandLiteral)
                .literal("reload")
                .permission(this.commandPermission)
                .handler(context -> {
                    beginReload();
                    final PlatformPlayer sender = context.sender().asPlayer();
                    final ServerPlayer player = sender == null ? null : sender.unwrap(ServerPlayer.class);
                    if (player != null) {
                        openPlayers(player, 0);
                        player.sendMessage(Component.text("Menu templates refreshed."));
                    }
                }));

    }

    @Override
    public synchronized void onDisable() {

        this.disabled = true;
        if (this.pendingReload != null) {
            this.pendingReload.cancel(false);
            this.pendingReload = null;
        }
        for (final Session session : this.sessions.values()) {
            session.player().closeInventory();
        }
        this.sessions.clear();

    }

    private void openPlayers(final @NotNull ServerPlayer viewer, final int requestedPage) {

        final List<TrackedPlayer> tracked = new ArrayList<>();
        this.environment.players().forEach(target -> {
            if (this.includeEmptyHistory || !target.history().isEmpty()) {
                tracked.add(target);
            }
        });
        final Comparator<TrackedPlayer> ordering = switch (this.selector.sortBy()) {
            case NAME -> Comparator.comparing(TrackedPlayer::name, String.CASE_INSENSITIVE_ORDER);
            case AVERAGE_PROBABILITY -> Comparator.comparingDouble(MenuTextRenderer::averageProbability);
            case LATEST_FAILURE -> Comparator.comparingLong(SpongeMenuAddon::latestFailure);
        };
        tracked.sort(this.selector.descending() ? ordering.reversed() : ordering);
        final List<Integer> slots = contentSlots(this.selector.allowedSlots(), this.selector.items());
        final int pages = pages(tracked.size(), slots.size());
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));
        final ViewableInventory inventory = SpongeMenuRenderer.players(
                (PluginContainer) this.environment.platform().pluginHandle(),
                this.renderer,
                this.selector,
                tracked,
                slots,
                page,
                pages
        );
        final InventoryMenu menu = inventory.asMenu();
        menu.setTitle(this.renderer.title(this.selector.title(), this.renderer.menuPlaceholders(page, pages, null)));
        menu.setReadOnly(true);
        final Session session = Session.players(viewer, inventory, page, tracked);
        bind(menu, session, (slotIndex, leftClick) -> {
            final List<Integer> content = contentSlots(this.selector.allowedSlots(), this.selector.items());
            final int contentIndex = content.indexOf(slotIndex);
            if (contentIndex >= 0) {
                final int targetIndex = session.page() * content.size() + contentIndex;
                if (targetIndex < session.players().size()) {
                    final TrackedPlayer target = session.players().get(targetIndex);
                    executeActions(viewer, leftClick
                                    ? this.selector.targetItem().leftClickActions()
                                    : this.selector.targetItem().rightClickActions(),
                            this.renderer.menuPlaceholders(0, 1, target), target, 0);
                }
            } else {
                runConfiguredActions(viewer, slotIndex, session.page(),
                        pages(session.players().size(), content.size()), null, leftClick);
            }
        });
        this.sessions.put(viewer.uniqueId(), session);
        menu.open(viewer);

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
        final ViewableInventory inventory = SpongeMenuRenderer.history(
                (PluginContainer) this.environment.platform().pluginHandle(),
                this.renderer,
                this.selected,
                target,
                groups,
                slots,
                page,
                pages
        );
        final InventoryMenu menu = inventory.asMenu();
        menu.setTitle(this.renderer.title(this.selected.title(), this.renderer.menuPlaceholders(page, pages, target)));
        menu.setReadOnly(true);
        final Session session = Session.history(viewer, inventory, page, target);
        bind(menu, session, (slotIndex, leftClick) -> {
            final List<Integer> content = contentSlots(this.selected.allowedSlots(), this.selected.items());
            if (content.contains(slotIndex)) {
                executeActions(viewer, leftClick
                                ? this.selected.entryItem().leftClickActions()
                                : this.selected.entryItem().rightClickActions(),
                        this.renderer.menuPlaceholders(session.page(), pages(historyGroups(target,
                                this.selected.entryItem().failuresPerEntry()).size(), content.size()), target),
                        target, session.page());
            } else {
                runConfiguredActions(viewer, slotIndex, session.page(), historyGroups(target,
                        this.selected.entryItem().failuresPerEntry()).size(), target, leftClick);
            }
        });
        this.sessions.put(viewer.uniqueId(), session);
        menu.open(viewer);

    }

    private void bind(
            final @NotNull InventoryMenu menu,
            final @NotNull Session session,
            final @NotNull BiConsumer<Integer, Boolean> action
    ) {

        menu.registerSlotClick((cause, container, slot, quantity, clickType) -> {
            if (container.viewer().uniqueId().equals(session.player().uniqueId())) {
                final PlatformPlayer platformPlayer = this.environment.platform().adaptPlayer(session.player());
                if (!this.commandPermission.isEmpty() && !platformPlayer.hasPermission(this.commandPermission)) {
                    session.player().closeInventory();
                    return false;
                }
                final boolean leftClick = clickType.equals(ClickTypes.CLICK_LEFT.get())
                        || clickType.equals(ClickTypes.SHIFT_CLICK_LEFT.get());
                final boolean rightClick = clickType.equals(ClickTypes.CLICK_RIGHT.get())
                        || clickType.equals(ClickTypes.SHIFT_CLICK_RIGHT.get());
                if (leftClick || rightClick) {
                    action.accept(session.inventory().slots().indexOf(slot), leftClick);
                }
            }
            return false;
        });
        menu.registerClose((cause, container) -> this.sessions.remove(container.viewer().uniqueId(), session));

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
            final @NotNull ServerPlayer viewer,
            final int slot,
            final int page,
            final int pageCount,
            final TrackedPlayer target,
            final boolean leftClick
    ) {

        final Map<String, ItemConfiguration> items = target == null ? this.selector.items() : this.selected.items();
        final Map<String, String> placeholders = this.renderer.menuPlaceholders(page, pageCount, target);
        for (final ItemConfiguration item : items.values()) {
            if (!item.slots().contains(slot)) {
                continue;
            }
            this.executeActions(viewer, leftClick ? item.leftClickActions() : item.rightClickActions(),
                    placeholders, target, page);
        }

    }

    private void executeActions(
            final @NotNull ServerPlayer viewer,
            final @NotNull List<String> actions,
            final @NotNull Map<String, String> placeholders,
            final TrackedPlayer target,
            final int page
    ) {

        final PlatformPlayer platformPlayer = this.environment.platform().adaptPlayer(viewer);
        for (final String action : actions) {
            if (this.executePlatformAction(viewer, action, target, placeholders, page)) {
                continue;
            }
            final var parsed = this.environment.actions().parse(action);
            if (parsed != null) {
                parsed.execute(platformPlayer, value -> this.renderer.replace(value, placeholders));
            }
        }

    }

    private boolean executePlatformAction(
            final @NotNull ServerPlayer viewer,
            final @NotNull String configuredAction,
            final TrackedPlayer target,
            final @NotNull Map<String, String> placeholders,
            final int page
    ) {

        final String action = configuredAction.trim();
        if (!action.startsWith("[") || action.indexOf(']') < 0) {
            return false;
        }
        final int end = action.indexOf(']');
        final String name = action.substring(1, end).trim().toLowerCase(Locale.ROOT);
        final String arguments = action.substring(end + 1).trim();
        if (SpongePlayerAction.execute(viewer, name, arguments, target, placeholders, this.renderer)) {
            return true;
        }
        switch (name) {
            case "select-clicked", "open-selected-target" -> {
                if (target != null) {
                    this.openHistory(viewer, target, 0);
                }
                return true;
            }
            case "open-target-selector", "back" -> {
                this.openPlayers(viewer, 0);
                return true;
            }
            case "switch-to-page" -> {
                final String resolved = this.renderer.replace(arguments, placeholders);
                try {
                    final int requestedPage = Integer.parseInt(resolved) - 1;
                    if (target == null) {
                        this.openPlayers(viewer, requestedPage);
                    } else {
                        this.openHistory(viewer, target, requestedPage);
                    }
                } catch (final NumberFormatException ignored) {
                }
                return true;
            }
            case "close-menu" -> {
                viewer.closeInventory();
                return true;
            }
            default -> {
                return false;
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

    private void loadSettings() {

        final String language = "en".equalsIgnoreCase(System.getProperty("user.language")) ? "en" : "ru";
        try {
            final ConfigurationNode config = this.environment.configs()
                    .file("main-config.yml", ConfigurationNode.class)
                    .defaults(getClass().getClassLoader(), language + "/main-config.yml")
                    .build()
                    .loadNode();
            final ConfigurationNode settings = config.node("settings");
            final AddonConfig parsedSettings = new AddonConfig(settings);
            final TargetSelectorConfiguration parsedSelector = new TargetSelectorConfiguration(this.environment.configs()
                    .file("templates/target-selector.yml", ConfigurationNode.class)
                    .defaults(getClass().getClassLoader(), language + "/templates/target-selector.yml")
                    .build()
                    .loadNode());
            final SelectedTargetConfiguration parsedSelected = new SelectedTargetConfiguration(this.environment.configs()
                    .file("templates/selected-target.yml", ConfigurationNode.class)
                    .defaults(getClass().getClassLoader(), language + "/templates/selected-target.yml")
                    .build()
                    .loadNode());
            final String literal = parsedSettings.argumentLiteral();
            final String permission = parsedSettings.permission();
            if (this.commandLiteral != null && (!this.commandLiteral.equals(literal)
                    || !this.commandPermission.equals(permission))) {
                throw new IllegalStateException("Changing the command literal or permission requires an addon restart");
            }
            this.commandLiteral = literal;
            this.commandPermission = permission;
            this.includeEmptyHistory = parsedSettings.includeEmptyHistory();
            this.selector = parsedSelector;
            this.selected = parsedSelected;
            this.renderer = new MenuTextRenderer(this.environment.text(), parsedSettings);
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not load main-config.yml", exception);
        }

    }

    private static long latestFailure(final @NotNull TrackedPlayer player) {

        final List<HistoryEntry> entries = player.history();
        return entries.isEmpty() ? Long.MIN_VALUE : entries.get(entries.size() - 1).timestamp();

    }

    private void refreshSessions() {

        for (final Session session : List.copyOf(this.sessions.values())) {
            if (session.historyTarget() == null) {
                openPlayers(session.player(), session.page());
            } else {
                openHistory(session.player(), session.historyTarget(), session.page());
            }
        }

    }

    private synchronized void beginReload() {

        if (this.disabled || this.pendingReload != null && !this.pendingReload.isDone()) {
            return;
        }
        this.pendingReload = CompletableFuture.runAsync(this::loadSettings).whenComplete((ignored, failure) -> {
            this.environment.scheduler().schedule(() -> {
                if (this.disabled) {
                    return;
                }
                if (failure == null) {
                    this.refreshSessions();
                } else {
                    throw new IllegalStateException("Could not reload menu templates", failure);
                }
            }, TickSchedule.instant());
        });

    }

    private final class InferenceRefreshListener {

        @Subscribe(priority = EventPriority.MONITOR)
        public void onInferenceResult(final @NotNull InferenceResultEvent event) {

            final TrackedPlayer changed = event.check().player();
            for (final Session session : List.copyOf(SpongeMenuAddon.this.sessions.values())) {
                SpongeMenuAddon.this.environment.scheduler().<ServerPlayer>mapEntity(
                        SpongeMenuAddon.this.environment.platform()::adaptPlayer
                ).scheduleEntity(session.player(), () -> {
                    if (session.historyTarget() == null) {
                        if (SpongeMenuAddon.this.selector.targetItem().updateOnFailure()) {
                            SpongeMenuAddon.this.openPlayers(session.player(), session.page());
                        }
                    } else if (SpongeMenuAddon.this.selected.entryItem().updateOnFailure()
                            && session.historyTarget().uniqueId().equals(changed.uniqueId())) {
                        SpongeMenuAddon.this.openHistory(session.player(), changed, session.page());
                    }
                }, TickSchedule.instant());
            }

        }

    }

    private record Session(
            @NotNull ServerPlayer player,
            @NotNull Inventory inventory,
            int page,
            @NotNull List<TrackedPlayer> players,
            TrackedPlayer historyTarget
    ) {

        private static Session players(
                final @NotNull ServerPlayer player,
                final @NotNull Inventory inventory,
                final int page,
                final @NotNull List<TrackedPlayer> targets
        ) {

            return new Session(player, inventory, page, List.copyOf(targets), null);

        }

        private static Session history(
                final @NotNull ServerPlayer player,
                final @NotNull Inventory inventory,
                final int page,
                final @NotNull TrackedPlayer target
        ) {

            return new Session(player, inventory, page, List.of(), target);

        }

    }

}

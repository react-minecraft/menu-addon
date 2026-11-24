package com.g4vrk.menuAddon.minestom;

import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.menuAddon.config.AddonConfig;
import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.menu.render.MenuItemData;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.ResolvableProfile;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;

/**
 * Minestom-native Menu addon runtime.
 */
public final class MinestomMenuAddon extends JavaAddon {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private EventNode<Event> eventNode;
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
        this.eventNode = EventNode.all("react-menu-addon");
        this.eventNode.addListener(InventoryPreClickEvent.class, this::onInventoryClick);
        this.eventNode.addListener(InventoryCloseEvent.class, this::onInventoryClose);
        MinecraftServer.getGlobalEventHandler().addChild(this.eventNode);

        final CommandRegistrar commands = this.environment.commands();
        commands.register("react", commands.command("react")
                .literal(this.commandLiteral)
                .permission(this.commandPermission)
                .handler(context -> {
                    final PlatformPlayer sender = context.sender().asPlayer();
                    if (sender == null) {
                        context.sender().sendMessage(Component.text("This command can only be used by a player."));
                        return;
                    }
                    final Player player = sender.unwrap(Player.class);
                    if (player != null) {
                        openPlayers(player, 0);
                    }
                }));
        commands.register("react", commands.command("react")
                .literal(this.commandLiteral)
                .literal("reload")
                .permission(this.commandPermission)
                .handler(context -> {
                    final PlatformPlayer sender = context.sender().asPlayer();
                    if (sender == null) {
                        context.sender().sendMessage(Component.text("This command can only be used by a player."));
                        return;
                    }
                    final Player player = sender.unwrap(Player.class);
                    if (player != null) {
                        beginReload();
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
        if (this.eventNode != null) {
            MinecraftServer.getGlobalEventHandler().removeChild(this.eventNode);
            this.eventNode = null;
        }
        for (final Session session : this.sessions.values()) {
            session.player().closeInventory();
        }
        this.sessions.clear();

    }

    private void openPlayers(final @NotNull Player viewer, final int requestedPage) {

        final List<TrackedPlayer> tracked = new ArrayList<>();
        this.environment.players().forEach(target -> {
            if (this.includeEmptyHistory || !target.history().isEmpty()) {
                tracked.add(target);
            }
        });
        final Comparator<TrackedPlayer> ordering = switch (this.selector.sortBy()) {
            case NAME -> Comparator.comparing(TrackedPlayer::name, String.CASE_INSENSITIVE_ORDER);
            case AVERAGE_PROBABILITY -> Comparator.comparingDouble(MenuTextRenderer::averageProbability);
            case LATEST_FAILURE -> Comparator.comparingLong(MinestomMenuAddon::latestFailure);
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
            inventory.setItemStack(slots.get(index - start), item(this.renderer.targetItem(
                    this.selector.targetItem(), target, target.history()
            ), target.name()));
        }
        this.sessions.put(viewer.getUuid(), Session.players(viewer, inventory, page, pages, tracked));
        viewer.openInventory(inventory);

    }

    private void openHistory(
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
            inventory.setItemStack(slots.get(index - start), item(this.renderer.historyGroupItem(
                    this.selected.entryItem(), target, groups.get(index), this.renderer.menuPlaceholders(page, pages, target)
            )));
        }
        this.sessions.put(viewer.getUuid(), Session.history(viewer, inventory, page, pages, target));
        viewer.openInventory(inventory);

    }

    private void onInventoryClick(final @NotNull InventoryPreClickEvent event) {

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
                    executeActions(event.getPlayer(), leftClick
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
            executeActions(event.getPlayer(), event.getClick() instanceof Click.Left
                            ? this.selected.entryItem().leftClickActions()
                            : this.selected.entryItem().rightClickActions(),
                    this.renderer.menuPlaceholders(session.page(), session.pages(), session.historyTarget()),
                    session.historyTarget(), session.page());
        } else {
            runConfiguredActions(event.getPlayer(), slot, session.page(), session.pages(), session.historyTarget(),
                    event.getClick() instanceof Click.Left);
        }

    }

    private void onInventoryClose(final @NotNull InventoryCloseEvent event) {

        final Session session = this.sessions.get(event.getPlayer().getUuid());
        if (session != null && session.inventory() == event.getInventory()) {
            this.sessions.remove(event.getPlayer().getUuid());
        }

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
            for (final Session session : List.copyOf(MinestomMenuAddon.this.sessions.values())) {
                MinestomMenuAddon.this.environment.scheduler().<Player>mapEntity(
                        MinestomMenuAddon.this.environment.platform()::adaptPlayer
                ).scheduleEntity(session.player(), () -> {
                    if (session.historyTarget() == null) {
                        if (MinestomMenuAddon.this.selector.targetItem().updateOnFailure()) {
                            MinestomMenuAddon.this.openPlayers(session.player(), session.page());
                        }
                    } else if (MinestomMenuAddon.this.selected.entryItem().updateOnFailure()
                            && session.historyTarget().uniqueId().equals(changed.uniqueId())) {
                        MinestomMenuAddon.this.openHistory(session.player(), changed, session.page());
                    }
                }, TickSchedule.instant());
            }

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

    private static @NotNull ItemStack item(final @NotNull String name, final @NotNull Component label) {

        Material material = Material.fromKey("minecraft:" + name);
        if (material == null) {
            material = Material.fromKey("minecraft:stone");
        }
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, label);

    }

    private static @NotNull ItemStack item(final @NotNull MenuItemData data) {

        return item(data, "");

    }

    private static @NotNull ItemStack item(
            final @NotNull MenuItemData data,
            final @NotNull String ownerName
    ) {

        Material material = Material.fromKey(data.material());
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack stack = ItemStack.of(material)
                .with(DataComponents.CUSTOM_NAME, data.name())
                .with(DataComponents.LORE, data.lore());
        if (data.owner() != null) {
            stack = stack.with(DataComponents.PROFILE, new ResolvableProfile(
                    new ResolvableProfile.Partial(ownerName, data.owner(), List.of())
            ));
        }
        return stack;

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
            final @NotNull Player viewer,
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
            final @NotNull Player viewer,
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
        switch (name) {
            case "gm", "gamemode", "set-game-mode" -> {
                final String mode = this.renderer.replace(arguments, placeholders).toUpperCase(Locale.ROOT);
                final GameMode gameMode = switch (mode) {
                    case "0", "SURVIVAL" -> GameMode.SURVIVAL;
                    case "1", "CREATIVE" -> GameMode.CREATIVE;
                    case "2", "ADVENTURE" -> GameMode.ADVENTURE;
                    case "3", "SPECTATOR" -> GameMode.SPECTATOR;
                    default -> null;
                };
                if (gameMode != null) {
                    viewer.setGameMode(gameMode);
                }
                return true;
            }
            case "tp-to-target", "teleport-to-target" -> {
                if (target != null && target.platform().online()) {
                    final Player nativeTarget = target.platform().unwrap(Player.class);
                    if (nativeTarget != null) {
                        viewer.setInstance(nativeTarget.getInstance(), nativeTarget.getPosition());
                    }
                }
                return true;
            }
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

    private void renderStatic(
            final @NotNull Inventory inventory,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders
    ) {

        for (final ItemConfiguration item : items.values()) {
            final MenuItemData rendered = this.renderer.staticItem(item, placeholders);
            for (final int slot : item.slots()) {
                inventory.setItemStack(slot, item(rendered));
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

    private record Session(
            @NotNull Player player,
            @NotNull Inventory inventory,
            int page,
            int pages,
            @NotNull List<TrackedPlayer> players,
            TrackedPlayer historyTarget
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

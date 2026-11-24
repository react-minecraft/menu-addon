package com.g4vrk.menuAddon.fabric;

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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Fabric-native Menu addon runtime.
 */
public final class FabricMenuAddon extends JavaAddon {

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
                        context.sender().sendMessage(net.kyori.adventure.text.Component.text(
                                "This command can only be used by a player."
                        ));
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
                        player.sendSystemMessage(Component.literal("Menu templates refreshed."));
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
            session.player().closeContainer();
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
            case LATEST_FAILURE -> Comparator.comparingLong(FabricMenuAddon::latestFailure);
        };
        tracked.sort(this.selector.descending() ? ordering.reversed() : ordering);
        final List<Integer> slots = contentSlots(this.selector.allowedSlots(), this.selector.items());
        final int pages = pages(tracked.size(), slots.size());
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));
        final SimpleContainer container = new SimpleContainer(this.selector.size());
        renderStatic(container, this.selector.items(), this.renderer.menuPlaceholders(page, pages, null));
        for (int index = page * slots.size(); index < Math.min((page + 1) * slots.size(), tracked.size()); index++) {
            final TrackedPlayer target = tracked.get(index);
            container.setItem(slots.get(index - page * slots.size()), item(this.renderer.targetItem(
                    this.selector.targetItem(), target, target.history()
            ), target.name()));
        }
        final String title = this.renderer.replace(
                this.selector.title(), this.renderer.menuPlaceholders(page, pages, null)
        );
        open(viewer, title, container, page, tracked, null, (slot, leftClick) -> {
            final int contentIndex = slots.indexOf(slot);
            if (contentIndex >= 0) {
                final int targetIndex = page * slots.size() + contentIndex;
                if (targetIndex < tracked.size()) {
                    final TrackedPlayer target = tracked.get(targetIndex);
                    executeActions(viewer, leftClick
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
            container.setItem(slots.get(index - page * slots.size()), item(this.renderer.historyGroupItem(
                    this.selected.entryItem(), target, groups.get(index), this.renderer.menuPlaceholders(page, pages, target)
            )));
        }
        final String title = this.renderer.replace(
                this.selected.title(), this.renderer.menuPlaceholders(page, pages, target)
        );
        open(viewer, title, container, page, List.of(), target, (slot, leftClick) -> {
            final List<Integer> contentSlots = contentSlots(this.selected.allowedSlots(), this.selected.items());
            if (contentSlots.contains(slot)) {
                executeActions(viewer, leftClick
                                ? this.selected.entryItem().leftClickActions()
                                : this.selected.entryItem().rightClickActions(),
                        this.renderer.menuPlaceholders(page, pages, target), target, page);
            } else {
                clickConfiguredItem(viewer, slot, page, pages, target, leftClick);
            }
        });

    }

    private void open(
            final @NotNull ServerPlayer viewer,
            final @NotNull String title,
            final @NotNull SimpleContainer container,
            final int page,
            final @NotNull List<TrackedPlayer> players,
            final TrackedPlayer historyTarget,
            final @NotNull BiConsumer<Integer, Boolean> action
    ) {

        final MenuProvider provider = new SimpleMenuProvider((syncId, inventory, nativePlayer) -> {
            final int rows = container.getContainerSize() / 9;
            final ChestMenu menu = new ChestMenu(menuType(rows), syncId, inventory, container, rows) {

                @Override
                public void clicked(
                        final int slot,
                        final int button,
                        final ContainerInput click,
                        final Player player
                ) {

                    if (player == viewer && slot >= 0 && slot < container.getContainerSize()
                            && (button == 0 || button == 1)) {
                        final PlatformPlayer platformPlayer = environment.platform().adaptPlayer(viewer);
                        if (!commandPermission.isEmpty() && !platformPlayer.hasPermission(commandPermission)) {
                            viewer.closeContainer();
                            return;
                        }
                        action.accept(slot, button == 0);
                    }

                }

                @Override
                public ItemStack quickMoveStack(final Player player, final int slot) {

                    return ItemStack.EMPTY;

                }

                @Override
                public void removed(final Player player) {

                    super.removed(player);
                    final Session session = sessions.get(viewer.getUUID());
                    if (session != null && session.menu() == this) {
                        sessions.remove(viewer.getUUID());
                    }

                }

            };
            this.sessions.put(viewer.getUUID(), new Session(viewer, menu, page, List.copyOf(players), historyTarget));
            return menu;
        }, Component.literal(title));
        viewer.openMenu(provider);

    }

    private static @NotNull ItemStack item(final @NotNull MenuItemData data) {

        return item(data, "");

    }

    private static @NotNull ItemStack item(
            final @NotNull net.minecraft.world.item.Item item,
            final @NotNull String name
    ) {

        final ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;

    }

    private static @NotNull ItemStack item(
            final @NotNull MenuItemData data,
            final @NotNull String ownerName
    ) {

        final Identifier key = Identifier.parse(data.material());
        final net.minecraft.world.item.Item material = BuiltInRegistries.ITEM.get(key)
                .map(reference -> reference.value())
                .orElse(Items.STONE);
        final ItemStack stack = new ItemStack(material == Items.AIR ? Items.STONE : material);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(plainText(data.name())));
        stack.set(DataComponents.LORE, new ItemLore(data.lore().stream()
                .<net.minecraft.network.chat.Component>map(line -> Component.literal(plainText(line)))
                .toList()));
        if (data.owner() != null) {
            stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(data.owner()));
        }
        return stack;

    }

    private static @NotNull MenuType<?> menuType(final int rows) {

        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };

    }

    private static @NotNull String plainText(final @NotNull net.kyori.adventure.text.Component component) {

        final StringBuilder result = new StringBuilder();
        if (component instanceof net.kyori.adventure.text.TextComponent text) {
            result.append(text.content());
        }
        for (final net.kyori.adventure.text.Component child : component.children()) {
            result.append(plainText(child));
        }
        return result.toString();

    }

    private void renderStatic(
            final @NotNull SimpleContainer container,
            final @NotNull Map<String, ItemConfiguration> items,
            final @NotNull Map<String, String> placeholders
    ) {

        for (final ItemConfiguration configured : items.values()) {
            final MenuItemData rendered = this.renderer.staticItem(configured, placeholders);
            for (final int slot : configured.slots()) {
                container.setItem(slot, item(rendered));
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
            final TrackedPlayer target,
            final boolean leftClick
    ) {

        final Map<String, ItemConfiguration> items = target == null ? this.selector.items() : this.selected.items();
        final Map<String, String> placeholders = this.renderer.menuPlaceholders(page, pages, target);
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
        switch (name) {
            case "gm", "gamemode", "set-game-mode" -> {
                final String mode = this.renderer.replace(arguments, placeholders).toUpperCase(Locale.ROOT);
                final GameType gameType = switch (mode) {
                    case "0", "SURVIVAL" -> GameType.SURVIVAL;
                    case "1", "CREATIVE" -> GameType.CREATIVE;
                    case "2", "ADVENTURE" -> GameType.ADVENTURE;
                    case "3", "SPECTATOR" -> GameType.SPECTATOR;
                    default -> null;
                };
                if (gameType != null) {
                    viewer.setGameMode(gameType);
                }
                return true;
            }
            case "tp-to-target", "teleport-to-target" -> {
                if (target != null && target.platform().online()) {
                    final ServerPlayer nativeTarget = target.platform().unwrap(ServerPlayer.class);
                    if (nativeTarget != null) {
                        viewer.teleportTo(nativeTarget.level(), nativeTarget.getX(), nativeTarget.getY(),
                                nativeTarget.getZ(), Set.of(), nativeTarget.getYRot(),
                                nativeTarget.getXRot(), true);
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
                viewer.closeContainer();
                return true;
            }
            default -> {
                return false;
            }
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
            final ConfigurationNode selectorNode = this.environment.configs()
                    .file("templates/target-selector.yml", ConfigurationNode.class)
                    .defaults(getClass().getClassLoader(), language + "/templates/target-selector.yml")
                    .build()
                    .loadNode();
            final ConfigurationNode selectedNode = this.environment.configs()
                    .file("templates/selected-target.yml", ConfigurationNode.class)
                    .defaults(getClass().getClassLoader(), language + "/templates/selected-target.yml")
                    .build()
                    .loadNode();
            final TargetSelectorConfiguration parsedSelector = new TargetSelectorConfiguration(selectorNode);
            final SelectedTargetConfiguration parsedSelected = new SelectedTargetConfiguration(selectedNode);
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
            for (final Session session : List.copyOf(FabricMenuAddon.this.sessions.values())) {
                FabricMenuAddon.this.environment.scheduler().<ServerPlayer>mapEntity(
                        FabricMenuAddon.this.environment.platform()::adaptPlayer
                ).scheduleEntity(session.player(), () -> {
                    if (session.historyTarget() == null) {
                        if (FabricMenuAddon.this.selector.targetItem().updateOnFailure()) {
                            FabricMenuAddon.this.openPlayers(session.player(), session.page());
                        }
                    } else if (FabricMenuAddon.this.selected.entryItem().updateOnFailure()
                            && session.historyTarget().uniqueId().equals(changed.uniqueId())) {
                        FabricMenuAddon.this.openHistory(session.player(), changed, session.page());
                    }
                }, TickSchedule.instant());
            }

        }

    }

    private record Session(
            @NotNull ServerPlayer player,
            @NotNull ChestMenu menu,
            int page,
            @NotNull List<TrackedPlayer> players,
            TrackedPlayer historyTarget
    ) {
    }

}

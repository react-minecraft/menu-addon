package com.g4vrk.menuAddon.minestom;

import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.menuAddon.config.AddonConfig;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.menuAddon.minestom.view.MenuView;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Minestom-native Menu addon runtime.
 */
public final class MinestomMenuAddon extends JavaAddon {

    private MenuView view;
    private EventNode<Event> eventNode;
    private AddonEnvironment environment;
    private String commandLiteral;
    private String commandPermission;
    private volatile boolean disabled;
    private volatile CompletableFuture<Void> pendingReload;

    @Override
    public void onEnable() {

        this.disabled = false;
        this.environment = environment();
        this.view = new MenuView(this.environment);
        loadSettings().run();
        this.environment.listeners().register(this.view);
        this.eventNode = EventNode.all("react-menu-addon");
        this.eventNode.addListener(InventoryPreClickEvent.class, this.view::onInventoryClick);
        this.eventNode.addListener(InventoryCloseEvent.class, this.view::onInventoryClose);
        this.eventNode.addListener(PlayerDisconnectEvent.class, this.view::onPlayerDisconnect);
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
                        this.view.openPlayers(player, 0);
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
                        player.sendMessage(Component.text("Menu reload requested."));
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
        this.view.close();

    }

    private Runnable loadSettings() {

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
            final MenuTextRenderer renderer = new MenuTextRenderer(this.environment.text(), parsedSettings);
            return () -> {
                if (this.commandLiteral != null && (!this.commandLiteral.equals(literal)
                        || !this.commandPermission.equals(permission))) {
                    throw new IllegalStateException("Changing the command literal or permission requires an addon restart");
                }
                this.commandLiteral = literal;
                this.commandPermission = permission;
                this.view.configure(permission, parsedSettings.includeEmptyHistory(), parsedSelector, parsedSelected, renderer);
            };
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not load main-config.yml", exception);
        }

    }

    private synchronized void beginReload() {

        if (this.disabled || this.pendingReload != null && !this.pendingReload.isDone()) {
            return;
        }
        this.pendingReload = CompletableFuture.supplyAsync(this::loadSettings).thenCompose(apply -> {
            final CompletableFuture<Void> applied = new CompletableFuture<>();
            this.environment.scheduler().schedule(() -> {
                if (this.disabled) {
                    applied.cancel(false);
                    return;
                }
                try {
                    apply.run();
                    this.view.refreshSessions();
                    applied.complete(null);
                } catch (final RuntimeException exception) {
                    applied.completeExceptionally(exception);
                }
            }, TickSchedule.instant());
            return applied;
        }).whenComplete((ignored, failure) -> {
            if (failure != null && !this.disabled) {
                System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "Could not reload menu templates", failure);
            }
        });

    }

}

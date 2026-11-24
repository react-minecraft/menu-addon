package com.g4vrk.menuAddon.fabric;

import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.menuAddon.config.AddonConfig;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.fabric.view.FabricMenuView;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Fabric-native Menu addon runtime.
 */
public final class FabricMenuAddon extends JavaAddon {

    private FabricMenuView view;
    private AddonEnvironment environment;
    private String commandLiteral;
    private String commandPermission;
    private volatile boolean disabled;
    private volatile CompletableFuture<Void> pendingReload;

    @Override
    public void onEnable() {

        this.disabled = false;
        this.environment = environment();
        this.view = new FabricMenuView(this.environment, () -> this.commandPermission);
        loadSettings();
        this.environment.listeners().register(this.view);
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
                    this.view.openPlayers(player, 0);
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
                        this.view.openPlayers(player, 0);
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
        this.view.closeAll();

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
            this.view.configure(
                    parsedSettings.includeEmptyHistory(), parsedSelector, parsedSelected,
                    new MenuTextRenderer(this.environment.text(), parsedSettings)
            );
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not load main-config.yml", exception);
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
                    this.view.refreshSessions();
                } else {
                    throw new IllegalStateException("Could not reload menu templates", failure);
                }
            }, TickSchedule.instant());
        });

    }

}

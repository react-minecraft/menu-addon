package com.g4vrk.menuAddon.sponge;

import com.g4vrk.menuAddon.config.AddonConfig;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.sponge.menu.SpongeMenuController;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.text.Component;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates the Sponge menu addon lifecycle and configuration.
 */
public final class SpongeMenuAddon extends JavaAddon {

    private AddonEnvironment environment;
    private SpongeMenuController controller;
    private String commandLiteral;
    private String commandPermission;
    private volatile boolean disabled;
    private volatile CompletableFuture<Void> pendingReload;

    @Override
    public void onEnable() {

        this.disabled = false;
        this.environment = environment();
        this.controller = new SpongeMenuController(this.environment);
        loadSettings().run();
        this.controller.registerListener();
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
                    this.controller.openPlayers(player, 0);
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
        this.controller.close();

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
                this.controller.configure(permission, parsedSettings.includeEmptyHistory(), parsedSelector, parsedSelected, renderer);
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
                    this.controller.refreshSessions();
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

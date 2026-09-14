package com.g4vrk.menuAddon;

import com.g4vrk.functionalConfiguration.Config;
import com.g4vrk.functionalConfiguration.loader.YamlConfigLoader;
import com.g4vrk.menuAddon.command.argument.MenuArgument;
import com.g4vrk.menuAddon.command.argument.MenuReloadArgument;
import com.g4vrk.menuAddon.config.AddonSettings;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.listener.HistoryEntryAddedListener;
import com.g4vrk.menuAddon.listener.MenuListener;
import com.g4vrk.menuAddon.menu.MenuManager;
import com.g4vrk.react.React;
import com.g4vrk.react.api.Factory;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.schedula.task.TickSchedule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReactMenuAddon extends JavaAddon {

    public static ReactMenuAddon INSTANCE;

    private volatile Config mainConfig;

    private MenuArgument menuArgument;
    private MenuReloadArgument reloadArgument;
    private MenuManager menuManager;
    private MenuListener menuListener;
    private HistoryEntryAddedListener historyEntryAddedListener;

    private final AtomicBoolean reloading = new AtomicBoolean();

    @Override
    public void onLoad() {

        INSTANCE = this;

    }

    @Override
    public void onEnable() {
        this.saveDefaultResource("main-config.yml");
        this.saveDefaultResource("templates/target-selector.yml");
        this.saveDefaultResource("templates/selected-target.yml");

        final LoadedModules configuration = this.loadConfigurableModules();
        this.mainConfig = configuration.mainConfig();

        final React react = React.INSTANCE;
        final Plugin plugin = react.getPlugin();
        final PluginManager pluginManager = Bukkit.getPluginManager();

        this.menuManager = new MenuManager(
                react.getPlayerRegistry(),
                react.getActionParser(),
                slf4jLogger(),
                configuration.settings(),
                configuration.targetSelector(),
                configuration.selectedTarget()
        );

        this.menuListener = new MenuListener();
        this.historyEntryAddedListener = new HistoryEntryAddedListener(this.menuManager);
        pluginManager.registerEvents(this.menuListener, plugin);
        pluginManager.registerEvents(this.historyEntryAddedListener, plugin);

        final Factory<Command.Builder<CommandSender>> builderFactory = () -> react
                .getCommandBuilderFactory()
                .create()
                .literal(configuration.settings().argumentLiteral())
                .permission(configuration.settings().permission());

        this.menuArgument = new MenuArgument(
                builderFactory,
                react.getActionParser(),
                this.menuManager
        );
        this.reloadArgument = new MenuReloadArgument(
                builderFactory,
                react.getScheduler(),
                react.getActionParser()
        );

        react.getCommandManager().command(this.menuArgument.build());
        react.getCommandManager().command(this.reloadArgument.build());

        slf4jLogger().info("ReactMenuAddon successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (this.menuListener != null) {
            HandlerList.unregisterAll(this.menuListener);
        }

        if (this.historyEntryAddedListener != null) {
            HandlerList.unregisterAll(this.historyEntryAddedListener);
        }

        if (this.menuManager != null) {
            this.menuManager.closeAll();
        }

        slf4jLogger().info("ReactMenuAddon successfully disabled!");
    }

    public @NotNull CompletableFuture<Void> reloadAsync() {
        if (!this.reloading.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Menu addon is already reloading")
            );
        }

        return CompletableFuture
                .supplyAsync(this::loadConfigurableModules)
                .thenCompose(configuration -> {
                    final CompletableFuture<Void> future = new CompletableFuture<>();

                    this.mainConfig = configuration.mainConfig();
                    this.menuArgument.reload();
                    this.reloadArgument.reload();

                    React.INSTANCE.getScheduler().schedule(
                            () -> {
                                try {
                                    this.menuManager.reload(
                                            configuration.settings(),
                                            configuration.targetSelector(),
                                            configuration.selectedTarget()
                                    );
                                    future.complete(null);
                                } catch (final Throwable throwable) {
                                    future.completeExceptionally(throwable);
                                }
                            },
                            TickSchedule.instant()
                    );

                    return future;
                })
                .whenComplete((ignored, throwable) -> {
                    this.reloading.set(false);

                    if (throwable != null) {
                        slf4jLogger().error("Failed to reload ReactMenuAddon", throwable);
                    }
                });
    }

    public @NotNull Config getMainConfig() {
        return this.mainConfig;
    }

    private void saveDefaultResource(
            final @NotNull String resourcePath
    ) {
        final Path path = directory().toPath().resolve(resourcePath);

        if (!path.toFile().exists()) {
            super.saveResource(resourcePath, false);
        }
    }

    private @NotNull LoadedModules loadConfigurableModules() {
        final Path directory = directory().toPath();
        final Config loadedMainConfig = this.loadConfig(
                directory.resolve("main-config.yml"),
                "main-config.yml"
        );
        final Config targetSelectorConfig = this.loadConfig(
                directory.resolve("templates").resolve("target-selector.yml"),
                "templates/target-selector.yml"
        );
        final Config selectedTargetConfig = this.loadConfig(
                directory.resolve("templates").resolve("selected-target.yml"),
                "templates/selected-target.yml"
        );

        return new LoadedModules(
                loadedMainConfig,
                new AddonSettings(loadedMainConfig.node("settings")),
                new TargetSelectorConfiguration(targetSelectorConfig.getRoot()),
                new SelectedTargetConfiguration(selectedTargetConfig.getRoot())
        );
    }

    private @NotNull Config loadConfig(
            final @NotNull Path path,
            final @NotNull String name
    ) {
        try {
            return new YamlConfigLoader().from(path);
        } catch (final IOException exception) {
            throw new RuntimeException("Failed to load " + name, exception);
        }
    }

    private record LoadedModules(
            @NotNull Config mainConfig,
            @NotNull AddonSettings settings,
            @NotNull TargetSelectorConfiguration targetSelector,
            @NotNull SelectedTargetConfiguration selectedTarget
    ) {
    }
}

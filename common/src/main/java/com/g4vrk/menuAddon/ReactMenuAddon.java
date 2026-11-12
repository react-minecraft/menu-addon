package com.g4vrk.menuAddon;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.menuAddon.command.MenuCommand;
import com.g4vrk.menuAddon.config.AddonSettings;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.listener.HistoryEntryAddedListener;
import com.g4vrk.menuAddon.listener.MenuListener;
import com.g4vrk.menuAddon.menu.MenuManager;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.extension.AddonEnvironment;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * Menu of tracked players and their inference history.
 *
 * <p>Commands and listeners go through the addon environment, so the plugin removes them
 * when the addon is disabled.</p>
 */
public final class ReactMenuAddon extends JavaAddon {

    private static final String MAIN_CONFIG = "main-config.yml";
    private static final String TARGET_SELECTOR = "templates/target-selector.yml";
    private static final String SELECTED_TARGET = "templates/selected-target.yml";

    private @Nullable CompletableFuture<Void> pendingReload;
    private boolean disabled;

    private MenuManager menuManager;
    private MenuCommand command;

    @Override
    public void onEnable() {

        saveDefault(MAIN_CONFIG);
        saveDefault(TARGET_SELECTOR);
        saveDefault(SELECTED_TARGET);

        final AddonEnvironment environment = environment();
        final ActionParser<Audience> parser = environment.actions();
        final LoadedModules configuration = load();

        this.menuManager = new MenuManager(
                environment.players(),
                parser,
                environment.text(),
                logger(),
                configuration.settings(),
                configuration.targetSelector(),
                configuration.selectedTarget()
        );

        environment.listeners().register(new MenuListener());
        environment.listeners().register(new HistoryEntryAddedListener(this.menuManager, environment.scheduler()));

        this.command = new MenuCommand(parser, this.menuManager, environment.scheduler(), this::reloadAsync, configuration.main());
        this.command.register(environment.commands(), configuration.settings().argumentLiteral(), configuration.settings().permission());

        logger().info("Menu is available as /react {}", configuration.settings().argumentLiteral());

    }

    @Override
    public void onDisable() {

        final CompletableFuture<Void> reload;
        synchronized (this) {
            this.disabled = true;
            reload = this.pendingReload;
            this.pendingReload = null;
        }
        if (reload != null) {
            reload.cancel(false);
        }

        if (this.menuManager != null) {
            this.menuManager.closeAll();
        }

    }

    /**
     * Reads the configs off the server thread, then applies them on it.
     *
     * @return completes once the open menus show the new templates
     */
    public synchronized @NotNull CompletableFuture<Void> reloadAsync() {

        if (this.disabled) {
            return CompletableFuture.failedFuture(new IllegalStateException("Menu addon is disabled"));
        }
        if (this.pendingReload != null && !this.pendingReload.isDone()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Menu addon is already reloading"));
        }

        final CompletableFuture<Void> result = CompletableFuture.supplyAsync(this::load).thenAcceptAsync(this::applyReload, task -> environment().scheduler().schedule(task, TickSchedule.instant()));
        this.pendingReload = result;
        result.whenComplete((ignored, failure) -> {
            if (failure != null && !result.isCancelled()) {
                logger().error("Could not reload the menu addon", failure);
            }
        });
        return result;

    }

    private synchronized void applyReload(final @NotNull LoadedModules configuration) {

        if (this.disabled) {
            return;
        }
        this.command.reload(configuration.main());
        this.menuManager.reload(
                configuration.settings(),
                configuration.targetSelector(),
                configuration.selectedTarget()
        );

    }

    private @NotNull LoadedModules load() {

        final CommentedConfigurationNode main = read(MAIN_CONFIG);
        return new LoadedModules(
                main,
                new AddonSettings(main.node("settings")),
                new TargetSelectorConfiguration(read(TARGET_SELECTOR)),
                new SelectedTargetConfiguration(read(SELECTED_TARGET))
        );

    }

    private @NotNull CommentedConfigurationNode read(final @NotNull String name) {

        try {
            return YamlConfigurationLoader.builder().path(directory().resolve(name)).build().load();
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not load " + name, exception);
        }

    }

    private void saveDefault(final @NotNull String name) {

        if (!Files.exists(directory().resolve(name))) {
            saveResource(name, false);
        }

    }

    private record LoadedModules(
            @NotNull CommentedConfigurationNode main,
            @NotNull AddonSettings settings,
            @NotNull TargetSelectorConfiguration targetSelector,
            @NotNull SelectedTargetConfiguration selectedTarget
    ) {
    }
}

package com.g4vrk.menuAddon.command;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.menuAddon.menu.MenuManager;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * {@code /react <literal>} opens the menu, {@code /react <literal> reload} reloads the addon.
 *
 * <p>Both are plain Cloud commands registered under the plugin's {@code /react} root, so they
 * share its aliases and are removed with the addon.</p>
 */
public final class MenuCommand {

    private static final String ROOT = "react";

    private final ActionParser<Audience> parser;
    private final MenuManager menus;
    private final Scheduler scheduler;
    private final Supplier<CompletableFuture<Void>> reload;

    private volatile Messages messages;

    public MenuCommand(
            final @NotNull ActionParser<Audience> parser,
            final @NotNull MenuManager menus,
            final @NotNull Scheduler scheduler,
            final @NotNull Supplier<CompletableFuture<Void>> reload,
            final @NotNull ConfigurationNode config
    ) {
        this.parser = parser;
        this.menus = menus;
        this.scheduler = scheduler;
        this.reload = reload;
        this.reload(config);
    }

    public void reload(final @NotNull ConfigurationNode config) {
        this.messages = new Messages(
                actions(config, "open", "on-console"),
                actions(config, "open", "on-denied"),
                actions(config, "reload", "on-start"),
                actions(config, "reload", "on-finish")
        );
    }

    public void register(
            final @NotNull CommandRegistrar commands,
            final @NotNull String literal,
            final @NotNull String permission
    ) {
        commands.register(ROOT, commands.command(ROOT)
                .literal(literal)
                .permission(permission)
                .handler(context -> open(context.sender())));

        commands.register(ROOT, commands.command(ROOT)
                .literal(literal)
                .literal("reload")
                .permission(permission)
                .handler(context -> reload(context.sender())));
    }

    private void open(final @NotNull CommandSender sender) {
        final Messages current = this.messages;

        if (!(sender instanceof Player player)) {
            current.console().run(sender);
            return;
        }

        if (!this.menus.canView(player)) {
            current.denied().run(sender, value -> value.replace("{permission}", this.menus.viewPermission()));
            return;
        }

        // Inventories belong to the player's region on Folia.
        this.scheduler.scheduleEntity(player, () -> this.menus.openTargetSelector(player, 0), TickSchedule.instant());
    }

    private void reload(final @NotNull CommandSender sender) {
        this.messages.reloadStart().run(sender);
        this.reload.get().thenRun(() -> this.scheduler.schedule(
                () -> this.messages.reloadFinish().run(sender),
                TickSchedule.instant()
        ));
    }

    private ExecutableActionList<? super Audience> actions(
            final @NotNull ConfigurationNode config,
            final @NotNull String... path
    ) {
        try {
            return this.parser.parseAll(config.node((Object[]) path).getList(String.class, List.of()));
        } catch (final SerializationException exception) {
            throw new IllegalStateException("Could not read the actions at " + String.join(".", path), exception);
        }
    }

    private record Messages(
            @NotNull ExecutableActionList<? super Audience> console,
            @NotNull ExecutableActionList<? super Audience> denied,
            @NotNull ExecutableActionList<? super Audience> reloadStart,
            @NotNull ExecutableActionList<? super Audience> reloadFinish
    ) {
    }

}

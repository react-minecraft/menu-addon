package com.g4vrk.menuAddon.command.argument;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalConfiguration.Config;
import com.g4vrk.menuAddon.ReactMenuAddon;
import com.g4vrk.menuAddon.menu.MenuManager;
import com.g4vrk.react.api.Factory;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.command.argument.LocalArgument;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.Collections;

public final class MenuArgument extends LocalArgument implements ReloadObserver {

    private final Factory<Command.Builder<CommandSender>> builderFactory;
    private final ActionParser<Audience> actionParser;
    private final MenuManager menuManager;

    private ExecutableActionList<? super Audience> consoleActions;
    private ExecutableActionList<? super Audience> deniedActions;

    public MenuArgument(
            final @NotNull Factory<Command.Builder<CommandSender>> builderFactory,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull MenuManager menuManager
    ) {
        this.builderFactory = builderFactory;
        this.actionParser = actionParser;
        this.menuManager = menuManager;

        this.reload();
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return this.builderFactory.create()
                .handler(context -> {
                    final CommandSender sender = context.sender();

                    if (!(sender instanceof Player player)) {
                        this.consoleActions.run(sender);
                        return;
                    }

                    if (!this.menuManager.canView(player)) {
                        this.deniedActions.run(
                                sender,
                                value -> value.replace("{permission}", this.menuManager.viewPermission())
                        );
                        return;
                    }

                    this.menuManager.openTargetSelector(player, 0);
                });
    }

    public void reload() {
        this.onReload(ReactMenuAddon.INSTANCE.getMainConfig());
    }

    @Override
    public void onReload(
            final @NotNull Config config
    ) {
        try {
            this.consoleActions = this.actionParser.parseAll(
                    config.node("open", "on-console").getList(String.class, Collections.emptyList())
            );
            this.deniedActions = this.actionParser.parseAll(
                    config.node("open", "on-denied").getList(String.class, Collections.emptyList())
            );
        } catch (final SerializationException exception) {
            throw new RuntimeException("Failed to load configuration values for menu argument", exception);
        }
    }
}

package com.g4vrk.menuAddon.command.argument;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.config.Config;
import com.g4vrk.menuAddon.ReactMenuAddon;
import com.g4vrk.react.api.Factory;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.Collections;

public final class MenuReloadArgument extends LocalArgument implements ReloadObserver {

    private final Factory<Command.Builder<CommandSender>> builderFactory;

    private final Scheduler scheduler;

    private final ActionParser<Audience> actionParser;

    private ExecutableActionList<? super Audience> startActions;
    private ExecutableActionList<? super Audience> finishActions;

    public MenuReloadArgument(
            @NotNull Factory<Command.Builder<CommandSender>> builderFactory,
            @NotNull Scheduler scheduler,
            @NotNull ActionParser<Audience> actionParser
    ) {
        this.builderFactory = builderFactory;
        this.scheduler = scheduler;
        this.actionParser = actionParser;

        this.reload();
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("reload")
                .handler(context -> {

                    final CommandSender sender = context.sender();

                    startActions.run(sender);

                    ReactMenuAddon.INSTANCE.reloadAsync().thenRun(
                            () -> scheduler.schedule(() -> finishActions.run(sender), TickSchedule.instant())
                    );

                });
    }

    public void reload() {

        final Config config = ReactMenuAddon.INSTANCE.getMainConfig();

        this.onReload(config);

    }

    @Override
    public void onReload(@NotNull Config config) {

        try {
            this.startActions = this.actionParser.parseAll(
                    config.node("reload", "on-start").getList(String.class, Collections.emptyList())
            );
            this.finishActions = this.actionParser.parseAll(
                    config.node("reload", "on-finish").getList(String.class, Collections.emptyList())
            );
        } catch (final SerializationException ex) {
            throw new RuntimeException("Failed to load configuration values for reload argument", ex);
        }

    }
}

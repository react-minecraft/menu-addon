package com.g4vrk.menuAddon.listener;

import com.g4vrk.menuAddon.menu.MenuManager;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.jetbrains.annotations.NotNull;

/**
 * Redraws open menus when a new model result reaches a player's history.
 */
public final class HistoryEntryAddedListener {

    private final MenuManager menuManager;
    private final Scheduler scheduler;

    public HistoryEntryAddedListener(
            final @NotNull MenuManager menuManager,
            final @NotNull Scheduler scheduler
    ) {
        this.menuManager = menuManager;
        this.scheduler = scheduler;
    }

    @Subscribe(priority = EventPriority.MONITOR)
    public void onInferenceResult(final @NotNull InferenceResultEvent event) {
        final TrackedPlayer player = event.check().player();
        this.scheduler.schedule(() -> this.menuManager.refresh(player), TickSchedule.instant());
    }
}

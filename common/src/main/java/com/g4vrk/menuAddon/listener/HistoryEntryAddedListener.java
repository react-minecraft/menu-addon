package com.g4vrk.menuAddon.listener;

import com.g4vrk.menuAddon.menu.MenuManager;
import com.g4vrk.react.React;
import com.g4vrk.react.api.event.InferenceHistoryEntryAddedEvent;
import com.g4vrk.schedula.task.TickSchedule;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public final class HistoryEntryAddedListener implements Listener {

    private final MenuManager menuManager;

    public HistoryEntryAddedListener(
            final @NotNull MenuManager menuManager
    ) {
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInferenceHistoryEntryAdded(
            final @NotNull InferenceHistoryEntryAddedEvent event
    ) {
        if (Bukkit.isPrimaryThread()) {
            this.menuManager.refresh(event.player());
            return;
        }

        React.INSTANCE.getScheduler().schedule(
                () -> this.menuManager.refresh(event.player()),
                TickSchedule.instant()
        );
    }
}

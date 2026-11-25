package com.g4vrk.menuAddon.menu;

import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.menuAddon.text.MenuTextRenderer;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.player.registry.TrackedPlayerRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared menu slot layout, pagination, target sorting, and history grouping utilities.
 */
public final class MenuLayout {

    private MenuLayout() {

    }

    public static void validate(
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {

        contentSlots(targetSelectorConfig.allowedSlots(), targetSelectorConfig.items());
        contentSlots(selectedTargetConfig.allowedSlots(), selectedTargetConfig.items());

    }

    public static @NotNull List<Integer> contentSlots(
            final @NotNull List<Integer> configuredSlots,
            final @NotNull Map<String, ItemConfiguration> items
    ) {

        final Set<Integer> reserved = new HashSet<>();

        for (final ItemConfiguration item : items.values()) {
            reserved.addAll(item.slots());
        }

        final List<Integer> result = new ArrayList<>(configuredSlots.size());

        for (final int slot : configuredSlots) {
            if (!reserved.contains(slot)) {
                result.add(slot);
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "Menu has no content slots after static item slots are reserved"
            );
        }

        return List.copyOf(result);

    }

    public static int pages(
            final int itemCount,
            final int pageSize
    ) {

        if (pageSize <= 0) {
            return 1;
        }

        return Math.max(1, (itemCount + pageSize - 1) / pageSize);

    }

    public static int clampPage(
            final int page,
            final int totalPages
    ) {

        return Math.max(0, Math.min(page, totalPages - 1));

    }

    public static @NotNull List<TrackedPlayer> targets(
            final @NotNull TrackedPlayerRegistry registry,
            final boolean includeEmptyHistory,
            final @NotNull TargetSelectorConfiguration config
    ) {

        final List<TrackedPlayer> targets = new ArrayList<>();

        for (final TrackedPlayer target : registry) {
            if (!includeEmptyHistory && target.history().isEmpty()) {
                continue;
            }

            targets.add(target);
        }

        Comparator<TrackedPlayer> comparator = switch (config.sortBy()) {
            case LATEST_FAILURE -> Comparator.comparingLong(MenuLayout::latestTimestamp);
            case AVERAGE_PROBABILITY -> Comparator.comparingDouble(MenuTextRenderer::averageProbability);
            case NAME -> Comparator.comparing(TrackedPlayer::name, String.CASE_INSENSITIVE_ORDER);
        };

        if (config.descending()) {
            comparator = comparator.reversed();
        }

        targets.sort(comparator.thenComparing(TrackedPlayer::name, String.CASE_INSENSITIVE_ORDER));
        return targets;

    }

    public static @NotNull List<List<HistoryEntry>> historyGroups(
            final @NotNull TrackedPlayer target,
            final int groupSize
    ) {

        final List<HistoryEntry> history = target.history();

        if (history.isEmpty()) {
            return List.of();
        }

        final int step = Math.max(1, groupSize);
        final List<List<HistoryEntry>> groups = new ArrayList<>((history.size() + step - 1) / step);

        for (int end = history.size(); end > 0; end -= step) {
            final int start = Math.max(0, end - step);
            final List<HistoryEntry> group = new ArrayList<>(history.subList(start, end));
            Collections.reverse(group);
            groups.add(List.copyOf(group));
        }

        return groups;

    }

    public static long latestTimestamp(
            final @NotNull TrackedPlayer target
    ) {

        final List<HistoryEntry> history = target.history();
        return history.isEmpty() ? Long.MIN_VALUE : history.get(history.size() - 1).timestamp();

    }

}

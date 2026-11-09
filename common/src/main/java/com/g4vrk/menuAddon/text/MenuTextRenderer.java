package com.g4vrk.menuAddon.text;

import com.g4vrk.menuAddon.config.AddonSettings;
import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import com.g4vrk.react.api.check.Check;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MenuTextRenderer {

    private static final String FORMATTED_FAILURES = "{target.failures:formatted}";

    private final TextFormatter text;
    private final AddonSettings settings;
    private final ElapsedTimeFormatter elapsedTimeFormatter;

    public MenuTextRenderer(
            final @NotNull TextFormatter text,
            final @NotNull AddonSettings settings
    ) {
        this.text = text;
        this.settings = settings;
        this.elapsedTimeFormatter = new ElapsedTimeFormatter(settings.elapsedTimeFormat());
    }

    public @NotNull Component title(
            final @NotNull String template,
            final @NotNull Map<String, String> placeholders
    ) {
        return this.component(template, placeholders);
    }

    public @NotNull ItemStack staticItem(
            final @NotNull ItemConfiguration config,
            final @NotNull Map<String, String> placeholders
    ) {
        final ItemStack item = new ItemStack(config.material());
        final ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(this.component(config.name(), placeholders));
        meta.lore(this.components(config.lore(), placeholders));

        if (meta instanceof PotionMeta potionMeta && config.color() != null) {
            potionMeta.setColor(config.color());
        }

        item.setItemMeta(meta);
        return item;
    }

    public @NotNull ItemStack targetItem(
            final @NotNull TargetSelectorConfiguration.TargetItem config,
            final @NotNull TrackedPlayer target
    ) {
        final Material material = config.usePlayerHead() ? Material.PLAYER_HEAD : config.material();
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        final List<HistoryEntry> history = target.history();
        final Map<String, String> placeholders = this.targetPlaceholders(target, history);

        meta.displayName(this.component(config.name(), placeholders));
        meta.lore(this.targetLore(config, target, history, placeholders));

        if (config.usePlayerHead() && meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target.bukkit());
        }

        item.setItemMeta(meta);
        return item;
    }

    public @NotNull ItemStack historyGroupItem(
            final @NotNull SelectedTargetConfiguration.EntryItem config,
            final @NotNull TrackedPlayer target,
            final @NotNull List<HistoryEntry> entries,
            final @NotNull Map<String, String> menuPlaceholders
    ) {
        final GroupStatistic statistic = GroupStatistic.calculate(entries);
        final Map<String, String> placeholders = new HashMap<>(menuPlaceholders);
        this.putGroupStatistic(placeholders, statistic);

        final Material material = this.historyGroupMaterial(config, statistic);
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(this.component(config.name(), placeholders));

        final List<Component> lore = new ArrayList<>();
        lore.addAll(this.components(config.loreHeader(), placeholders));

        for (final HistoryEntry entry : entries) {
            final Map<String, String> entryPlaceholders = new HashMap<>(placeholders);
            this.putEntry(entryPlaceholders, target, entry, config.decimalFormat());
            lore.add(this.component(config.failureFormat(), entryPlaceholders));
        }

        lore.addAll(this.components(config.loreFooter(), placeholders));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public @NotNull Map<String, String> menuPlaceholders(
            final int page,
            final int totalPages,
            final TrackedPlayer target
    ) {
        final Map<String, String> placeholders = new HashMap<>();
        final int normalizedPages = Math.max(1, totalPages);
        final int current = Math.max(1, Math.min(page + 1, normalizedPages));

        placeholders.put("pagination:current", Integer.toString(current));
        placeholders.put("pagination:pages", Integer.toString(normalizedPages));
        placeholders.put("pagination:next", Integer.toString(Math.min(normalizedPages, current + 1)));
        placeholders.put("pagination:previous", Integer.toString(Math.max(1, current - 1)));

        if (target != null) {
            placeholders.put("player.name", target.name());
            placeholders.put("player.uuid", target.uniqueId().toString());
            placeholders.put("target.name", target.name());
            placeholders.put("target.uuid", target.uniqueId().toString());
        }

        return placeholders;
    }

    public @NotNull Component component(
            final @NotNull String template,
            final @NotNull Map<String, String> placeholders
    ) {
        return this.text.format(this.replace(template, placeholders)).decoration(TextDecoration.ITALIC, false);
    }

    public @NotNull String replace(
            final @NotNull String template,
            final @NotNull Map<String, String> placeholders
    ) {
        String result = template;

        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('{' + entry.getKey() + '}', entry.getValue());
        }

        return result;
    }

    private @NotNull List<Component> targetLore(
            final @NotNull TargetSelectorConfiguration.TargetItem config,
            final @NotNull TrackedPlayer target,
            final @NotNull List<HistoryEntry> history,
            final @NotNull Map<String, String> placeholders
    ) {
        final List<String> failureLines = this.failureLines(config.failures(), target, history);
        final List<Component> result = new ArrayList<>();

        for (final String line : config.lore()) {
            if (!line.contains(FORMATTED_FAILURES)) {
                result.add(this.component(line, placeholders));
                continue;
            }

            for (final String failureLine : failureLines) {
                result.add(this.component(
                        line.replace(FORMATTED_FAILURES, failureLine),
                        placeholders
                ));
            }
        }

        return result;
    }

    private @NotNull List<String> failureLines(
            final @NotNull TargetSelectorConfiguration.FailureList config,
            final @NotNull TrackedPlayer target,
            final @NotNull List<HistoryEntry> history
    ) {
        if (history.isEmpty() || config.max() == 0) {
            return List.of(config.empty());
        }

        final int amount = Math.min(config.max(), history.size());
        final List<HistoryEntry> entries = new ArrayList<>(amount);

        for (int index = history.size() - amount; index < history.size(); index++) {
            entries.add(history.get(index));
        }

        if (config.newestOnLeft()) {
            Collections.reverse(entries);
        }

        final List<String> rendered = new ArrayList<>(entries.size());

        for (final HistoryEntry entry : entries) {
            final Map<String, String> placeholders = new HashMap<>();
            this.putEntry(placeholders, target, entry, config.decimalFormat());
            rendered.add(this.replace(config.format(), placeholders));
        }

        final List<String> lines = new ArrayList<>();

        for (int start = 0; start < rendered.size(); start += config.perLine()) {
            lines.add(String.join(
                    config.separator(),
                    rendered.subList(start, Math.min(rendered.size(), start + config.perLine()))
            ));
        }

        return lines;
    }

    private @NotNull Map<String, String> targetPlaceholders(
            final @NotNull TrackedPlayer target,
            final @NotNull List<HistoryEntry> history
    ) {
        final Map<String, String> placeholders = this.menuPlaceholders(0, 1, target);
        final double average = averageProbability(history);
        final String probability = this.format(average, this.settings.probabilityFormat());
        final long latestInference = history.isEmpty() ? 0L : history.get(history.size() - 1).timestamp();
        // The public API exposes whether a fight is in progress, not when its last hit landed.
        final long latestTimestamp = target.inCombat() ? System.currentTimeMillis() : latestInference;

        placeholders.put("target.history-size", Integer.toString(history.size()));
        placeholders.put("target.last-hit", Long.toString(latestTimestamp));
        placeholders.put("target.last-hit:formatted", this.elapsedTimeFormatter.formatTimestamp(latestTimestamp));
        placeholders.put("target.statistic.average-probability", probability);
        placeholders.put(
                "target.statistic.average-probability:colored",
                this.coloredProbability(average, probability)
        );

        return placeholders;
    }

    private void putGroupStatistic(
            final @NotNull Map<String, String> placeholders,
            final @NotNull GroupStatistic statistic
    ) {
        final String probability = this.format(statistic.averageProbability(), this.settings.probabilityFormat());

        placeholders.put("statistic.average-probability", probability);
        placeholders.put(
                "statistic.average-probability:colored",
                this.coloredProbability(statistic.averageProbability(), probability)
        );
    }

    private void putEntry(
            final @NotNull Map<String, String> placeholders,
            final @NotNull TrackedPlayer target,
            final @NotNull HistoryEntry entry,
            final @NotNull String decimalPattern
    ) {
        final String probability = this.format(entry.probability(), decimalPattern);

        placeholders.put("probability", probability);
        placeholders.put("probability:colored", this.coloredProbability(entry.probability(), probability));
        final Check check = target.check(entry.check());
        placeholders.put("check.name", check == null ? entry.check() : check.name());
        placeholders.put("check.id", entry.check());
        placeholders.put("passed-time", Long.toString(entry.timestamp()));
        placeholders.put("passed-time:formatted", this.elapsedTimeFormatter.formatTimestamp(entry.timestamp()));
    }

    private @NotNull Material historyGroupMaterial(
            final @NotNull SelectedTargetConfiguration.EntryItem config,
            final @NotNull GroupStatistic statistic
    ) {
        if (!config.coloredPane().enabled()) {
            return config.material();
        }

        final DyeColor dyeColor = dyeColor(statistic.averageProbability());
        final Material pane = Material.matchMaterial(dyeColor.name() + "_STAINED_GLASS_PANE");
        return pane == null ? config.material() : pane;
    }

    private static @NotNull DyeColor dyeColor(
            final double rawValue
    ) {
        final double value = Math.max(0.0D, Math.min(1.0D, rawValue));
        final int level = Math.min(4, (int) Math.floor(value * 5.0D));

        return switch (level) {
                case 0 -> DyeColor.GREEN;
                case 1 -> DyeColor.LIME;
                case 2 -> DyeColor.YELLOW;
                case 3 -> DyeColor.ORANGE;
                default -> DyeColor.RED;
        };
    }

    private @NotNull List<Component> components(
            final @NotNull List<String> templates,
            final @NotNull Map<String, String> placeholders
    ) {
        final List<Component> result = new ArrayList<>(templates.size());

        for (final String template : templates) {
            result.add(this.component(template, placeholders));
        }

        return result;
    }

    private @NotNull String coloredProbability(
            final double value,
            final @NotNull String formatted
    ) {
        return color(probabilityColor(value), formatted);
    }

    private static @NotNull String color(
            final @NotNull TextColor color,
            final @NotNull String value
    ) {
        return String.format(Locale.ROOT, "<color:#%06X>%s</color>", color.value(), value);
    }

    private @NotNull String format(
            final double value,
            final @NotNull String pattern
    ) {
        final DecimalFormat format = new DecimalFormat(
                pattern,
                DecimalFormatSymbols.getInstance(Locale.US)
        );
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /** Mean probability of a player's retained history, or 0 when it is empty. */
    public static double averageProbability(final @NotNull TrackedPlayer target) {
        return averageProbability(target.history());
    }

    private static double averageProbability(final @NotNull List<HistoryEntry> history) {
        return GroupStatistic.calculate(history).averageProbability();
    }

    private static @NotNull TextColor probabilityColor(final double probability) {
        final double normalized = Math.min(1.0D, Math.max(probability, 0.0D));
        if (normalized < 0.5D) {
            return TextColor.color((int) (normalized * 2.0D * 255), 255, 0);
        }
        return TextColor.color(255, (int) ((1.0D - (normalized - 0.5D) * 2.0D) * 255), 0);
    }

    private record GroupStatistic(
            double averageProbability
    ) {

        private static @NotNull GroupStatistic calculate(
                final @NotNull List<HistoryEntry> entries
        ) {
            if (entries.isEmpty()) {
                return new GroupStatistic(0.0D);
            }

            double probability = 0.0D;

            for (final HistoryEntry entry : entries) {
                probability += entry.probability();
            }

            return new GroupStatistic(probability / entries.size());
        }
    }
}

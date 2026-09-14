package com.g4vrk.menuAddon.text;

import com.g4vrk.menuAddon.config.AddonSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ElapsedTimeFormatter {

    private final AddonSettings.ElapsedTimeFormat format;

    public ElapsedTimeFormatter(
            final @NotNull AddonSettings.ElapsedTimeFormat format
    ) {
        this.format = format;
    }

    public @NotNull String formatTimestamp(
            final long timestamp
    ) {
        if (timestamp <= 0L) {
            return this.format.never();
        }

        final long elapsedMillis = Math.max(0L, System.currentTimeMillis() - timestamp);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);

        if (seconds == 0L) {
            return this.format.now();
        }

        final long days = seconds / 86_400L;
        seconds %= 86_400L;
        final long hours = seconds / 3_600L;
        seconds %= 3_600L;
        final long minutes = seconds / 60L;
        seconds %= 60L;

        final List<String> parts = new ArrayList<>(4);
        add(parts, days, this.format.days());
        add(parts, hours, this.format.hours());
        add(parts, minutes, this.format.minutes());
        add(parts, seconds, this.format.seconds());

        if (parts.isEmpty()) {
            return this.format.now();
        }

        return String.join(
                this.format.separator(),
                parts.subList(0, Math.min(parts.size(), this.format.maxParts()))
        );
    }

    private static void add(
            final @NotNull List<String> parts,
            final long value,
            final @NotNull String template
    ) {
        if (value > 0L) {
            parts.add(template.replace("{value}", Long.toString(value)));
        }
    }
}

package com.g4vrk.menuAddon.config;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

public final class AddonSettings {

    private final String argumentLiteral;
    private final String permission;
    private final boolean includeEmptyHistory;
    private final String probabilityFormat;
    private final String confidenceFormat;
    private final ElapsedTimeFormat elapsedTimeFormat;

    public AddonSettings(
            final @NotNull ConfigurationNode node
    ) {
        final String configuredLiteral = node.node("argument-literal").getString("menu").trim();

        if (!configuredLiteral.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "settings.argument-literal may only contain lowercase letters, digits, '_' and '-'"
            );
        }

        this.argumentLiteral = configuredLiteral;
        this.permission = node.node("permission").getString("react.menu.view").trim();
        this.includeEmptyHistory = node.node("include-empty-history").getBoolean(false);
        this.probabilityFormat = ConfigurationUtil.decimalFormat(
                node.node("format", "probability"),
                "0.00000"
        );
        this.confidenceFormat = ConfigurationUtil.decimalFormat(
                node.node("format", "confidence"),
                "0.00000"
        );
        this.elapsedTimeFormat = new ElapsedTimeFormat(
                node.node("format", "elapsed-time")
        );
    }

    public @NotNull String argumentLiteral() {
        return this.argumentLiteral;
    }

    public @NotNull String permission() {
        return this.permission;
    }

    public boolean includeEmptyHistory() {
        return this.includeEmptyHistory;
    }

    public @NotNull String probabilityFormat() {
        return this.probabilityFormat;
    }

    public @NotNull String confidenceFormat() {
        return this.confidenceFormat;
    }

    public @NotNull ElapsedTimeFormat elapsedTimeFormat() {
        return this.elapsedTimeFormat;
    }

    public record ElapsedTimeFormat(
            @NotNull String now,
            @NotNull String never,
            @NotNull String separator,
            int maxParts,
            @NotNull String days,
            @NotNull String hours,
            @NotNull String minutes,
            @NotNull String seconds
    ) {

        private ElapsedTimeFormat(
                final @NotNull ConfigurationNode node
        ) {
            this(
                    node.node("now").getString("только что"),
                    node.node("never").getString("никогда"),
                    node.node("separator").getString(" "),
                    Math.max(1, node.node("max-parts").getInt(2)),
                    node.node("days").getString("{value}д"),
                    node.node("hours").getString("{value}ч"),
                    node.node("minutes").getString("{value}м"),
                    node.node("seconds").getString("{value}с")
            );
        }
    }
}

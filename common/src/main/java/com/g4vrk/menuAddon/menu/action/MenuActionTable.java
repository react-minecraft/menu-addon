package com.g4vrk.menuAddon.menu.action;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Lookup table for precompiled menu action lists tied to loaded configuration objects.
 *
 * @param <N> native platform player type when platform-specific actions are supported
 */
public final class MenuActionTable<N> {

    private final Map<List<String>, List<CompiledMenuAction<N>>> compiled;
    private final MenuActionCompiler<N> fallbackCompiler;

    public MenuActionTable(
            final @NotNull Map<List<String>, List<CompiledMenuAction<N>>> compiled,
            final @NotNull MenuActionCompiler<N> fallbackCompiler
    ) {

        this.compiled = compiled;
        this.fallbackCompiler = fallbackCompiler;

    }

    public @NotNull List<CompiledMenuAction<N>> resolve(
            final @NotNull List<String> configuredActions
    ) {

        if (configuredActions.isEmpty()) {
            return List.of();
        }

        final List<CompiledMenuAction<N>> cached = this.compiled.get(configuredActions);

        if (cached != null) {
            return cached;
        }

        return this.fallbackCompiler.compile(configuredActions);

    }

}

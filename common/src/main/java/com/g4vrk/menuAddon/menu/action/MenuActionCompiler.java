package com.g4vrk.menuAddon.menu.action;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.menuAddon.config.ItemConfiguration;
import com.g4vrk.menuAddon.config.SelectedTargetConfiguration;
import com.g4vrk.menuAddon.config.TargetSelectorConfiguration;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Precompiles configured menu click action lists during template loading.
 *
 * @param <N> native platform player type when platform-specific actions are supported
 */
public final class MenuActionCompiler<N> {

    private static final Set<String> LOCAL_ACTIONS = Set.of(
            "gm",
            "gamemode",
            "set-game-mode",
            "tp-to-target",
            "teleport-to-target",
            "select-clicked",
            "open-selected-target",
            "open-target-selector",
            "back",
            "switch-to-page",
            "close-menu"
    );

    private final ActionParser<Audience> audienceParser;
    private final @Nullable ActionParser<N> nativeParser;
    private final @Nullable Consumer<String> unknownLogger;
    private final Set<String> reportedUnknown = ConcurrentHashMap.newKeySet();

    public MenuActionCompiler(
            final @NotNull ActionParser<Audience> audienceParser,
            final @Nullable ActionParser<N> nativeParser,
            final @Nullable Consumer<String> unknownLogger
    ) {

        this.audienceParser = audienceParser;
        this.nativeParser = nativeParser;
        this.unknownLogger = unknownLogger;

    }

    public @NotNull MenuActionTable<N> compileTable(
            final @NotNull TargetSelectorConfiguration targetSelectorConfig,
            final @NotNull SelectedTargetConfiguration selectedTargetConfig
    ) {

        final Map<List<String>, List<CompiledMenuAction<N>>> compiled = new IdentityHashMap<>();

        compileEntry(compiled, targetSelectorConfig.targetItem().leftClickActions());
        compileEntry(compiled, targetSelectorConfig.targetItem().rightClickActions());

        for (final ItemConfiguration item : targetSelectorConfig.items().values()) {
            compileEntry(compiled, item.leftClickActions());
            compileEntry(compiled, item.rightClickActions());
        }

        compileEntry(compiled, selectedTargetConfig.entryItem().leftClickActions());
        compileEntry(compiled, selectedTargetConfig.entryItem().rightClickActions());

        for (final ItemConfiguration item : selectedTargetConfig.items().values()) {
            compileEntry(compiled, item.leftClickActions());
            compileEntry(compiled, item.rightClickActions());
        }

        return new MenuActionTable<>(compiled, this);

    }

    public @NotNull List<CompiledMenuAction<N>> compile(
            final @NotNull List<String> configuredActions
    ) {

        if (configuredActions.isEmpty()) {
            return List.of();
        }

        final List<CompiledMenuAction<N>> result = new ArrayList<>(configuredActions.size());

        for (final String configuredAction : configuredActions) {
            final ParsedMenuAction parsed = ParsedMenuAction.parse(configuredAction);

            if (parsed == null) {
                continue;
            }

            if (LOCAL_ACTIONS.contains(parsed.name())) {
                result.add(CompiledMenuAction.localAction(parsed));
                continue;
            }

            if (this.nativeParser != null) {
                final ExecutableAction<? super N> nativeAction = this.nativeParser.parse(configuredAction);

                if (nativeAction != null) {
                    result.add(CompiledMenuAction.nativePlayerAction(nativeAction));
                    continue;
                }
            }

            final ExecutableAction<? super Audience> audienceAction = this.audienceParser.parse(configuredAction);

            if (audienceAction != null) {
                result.add(CompiledMenuAction.audience(audienceAction));
                continue;
            }

            if (this.unknownLogger != null && this.reportedUnknown.add(parsed.name())) {
                this.unknownLogger.accept(parsed.name());
            }
        }

        return List.copyOf(result);

    }

    private void compileEntry(
            final @NotNull Map<List<String>, List<CompiledMenuAction<N>>> compiled,
            final @NotNull List<String> rawActions
    ) {

        compiled.computeIfAbsent(rawActions, this::compile);

    }

}

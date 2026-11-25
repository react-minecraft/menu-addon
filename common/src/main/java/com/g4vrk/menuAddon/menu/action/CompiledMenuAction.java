package com.g4vrk.menuAddon.menu.action;

import com.g4vrk.functionalActions.ExecutableAction;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Precompiled menu action backed by either a built-in menu operation or FunctionalActions.
 *
 * @param <N> native platform player type when platform-specific actions are supported
 */
public record CompiledMenuAction<N>(
        @Nullable ParsedMenuAction local,
        @Nullable ExecutableAction<? super N> nativeAction,
        @Nullable ExecutableAction<? super Audience> audienceAction
) {

    public static <N> @NotNull CompiledMenuAction<N> localAction(
            final @NotNull ParsedMenuAction local
    ) {

        return new CompiledMenuAction<>(local, null, null);

    }

    public static <N> @NotNull CompiledMenuAction<N> nativePlayerAction(
            final @NotNull ExecutableAction<? super N> nativeAction
    ) {

        return new CompiledMenuAction<>(null, nativeAction, null);

    }

    public static <N> @NotNull CompiledMenuAction<N> audience(
            final @NotNull ExecutableAction<? super Audience> audienceAction
    ) {

        return new CompiledMenuAction<>(null, null, audienceAction);

    }

}

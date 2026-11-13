package com.g4vrk.menuAddon;

import com.g4vrk.react.api.extension.ExtensionId;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrap;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrapContext;
import org.jetbrains.annotations.NotNull;

/**
 * Declares the stable identifier used by other React extensions.
 */
public final class AddonBootstrap implements ExtensionBootstrap {

    @Override
    public void bootstrap(final @NotNull ExtensionBootstrapContext context) {

        context.identify(ExtensionId.addon("menu"), 1);

    }

}

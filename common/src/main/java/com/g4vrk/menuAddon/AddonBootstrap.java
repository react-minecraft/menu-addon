package com.g4vrk.menuAddon;

import com.g4vrk.react.api.extension.ExtensionId;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrap;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrapContext;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Declares the stable identifier used by other React extensions.
 */
public final class AddonBootstrap implements ExtensionBootstrap {

    @Override
    public void bootstrap(final @NotNull ExtensionBootstrapContext context) {

        context.identify(ExtensionId.addon("menu"), 1);
        context.library(
                URI.create("https://repo.maven.apache.org/maven2/com/github/cryptomorin/XSeries/14.0.0/XSeries-14.0.0.jar"),
                "0cfbe934bf11beebae3d6f54f554beba9e547a556976a9643eed6ab3a987e5f0"
        );

    }

}

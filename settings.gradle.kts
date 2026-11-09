rootProject.name = "ReactMenuAddon"

include(
    "common"
)

val localPlugin = file("../plugin")
if (System.getenv("CI") == null && localPlugin.resolve("common").isDirectory) {
    includeBuild(localPlugin) {
        dependencySubstitution {
            substitute(module("com.github.react-minecraft.plugin:react-api")).using(project(":api"))
            substitute(module("com.github.react-minecraft.plugin:react-common")).using(project(":common"))
        }
    }
}

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
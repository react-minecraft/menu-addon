plugins {
    alias(libs.plugins.fabric.loom)
    id("java-library")
}

val addonCommon by configurations.creating {
    isCanBeConsumed = false
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://jitpack.io")
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.fabric.minecraft.get()}")
    compileOnly(libs.fabric.loader)
    compileOnly(libs.fabric.api)

    api(project(":common"))
    addonCommon(project(":common"))
    compileOnly(libs.cloud.core)
    compileOnly(libs.react.api)
    compileOnly(libs.react.common)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.adventure.api)
}

tasks.processResources {
    from(project(":common").file("src/main/resources")) {
        exclude("react-addon.yml")
        exclude("META-INF/services/com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrap")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("menu-addon-fabric-${project.version}.jar")
    configurations = listOf(addonCommon)
}

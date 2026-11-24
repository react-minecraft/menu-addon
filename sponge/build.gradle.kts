import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("java-library")
}

configurations.configureEach {
    if (name == "compileClasspath" || name.endsWith("CompileClasspath")) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

repositories {
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    api(project(":common"))
    compileOnly(libs.sponge.api)
    compileOnly(libs.cloud.core)
    compileOnly(libs.react.api)
    compileOnly(libs.react.common)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)
    compileOnly(libs.configurate.yaml)
}

tasks.processResources {
    from(project(":common").file("src/main/resources")) {
        exclude("react-addon.yml")
        exclude("META-INF/services/com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrap")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("menu-addon-sponge-${project.version}.jar")
}

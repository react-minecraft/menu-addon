import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("java-library")
}

dependencies {
    api(project(":common"))

    compileOnly(libs.minestom)
    compileOnly(libs.cloud.core)
    compileOnly(libs.react.api)
    compileOnly(libs.react.common)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)
    compileOnly(libs.configurate.yaml)
}

configurations.compileClasspath {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
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

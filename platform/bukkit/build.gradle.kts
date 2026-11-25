import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("java-library")
}

repositories {
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.xseries)
    compileOnly(libs.react.api)
    compileOnly(libs.react.common)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)
    compileOnly(libs.cloud.paper)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.packetevents)
}

tasks.processResources {
    from(project(":common").file("src/main/resources")) {
        exclude("react-addon.yml")
    }
}

tasks.named<ShadowJar>("shadowJar") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    val commonClasses = project(":common").extensions.getByType<SourceSetContainer>()
        .named("main")
        .map { it.output.classesDirs }
    from(commonClasses)
}

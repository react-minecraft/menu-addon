import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import util.VersionUtility
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("java-library")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

version = VersionUtility.version(project, version.toString())

subprojects {

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "com.gradleup.shadow")

    group = rootProject.group
    version = rootProject.version
    description = rootProject.description


    repositories {

        mavenCentral()

        maven {

            url = uri("https://repo.papermc.io/repository/maven-public/")

        }

        maven {

            url = uri("https://jitpack.io/")

        }

    }

    plugins.withType<JavaPlugin> {

        dependencies {
            "testImplementation"(
                platform(rootProject.libs.junit.bom)
            )

            "testImplementation"(
                rootProject.libs.junit.jupiter
            )

            "testRuntimeOnly"(
                rootProject.libs.junit.platform.launcher
            )
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

    extensions.configure<JavaPluginExtension> {

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        withSourcesJar()

    }


    tasks {

        withType<JavaCompile>().configureEach {

            options.encoding = "UTF-8"

        }

        withType<ShadowJar>().configureEach {

            archiveFileName = "menu-addon-${project.version}.jar"
            archiveClassifier = ""

            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            mergeServiceFiles()

        }

        withType<RunServer>().configureEach {
            minecraftVersion(libs.versions.minecraft.get())
            jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
        }

        withType<ProcessResources>().configureEach {
            val props = mapOf(
                "version" to version,
                "description" to description,
                "name" to rootProject.name
            )
            inputs.properties(props)
            filesMatching("react-addon.yml") {
                expand(props)
            }
        }

        named("build") {

            dependsOn(named("shadowJar"))

        }

    }


    extensions.configure<PublishingExtension> {

        publications {

            create<MavenPublication>("maven") {

                groupId = System.getenv("GROUP") ?: project.group.toString()
                artifactId = rootProject.name + "-" + project.name
                version = System.getenv("VERSION") ?: project.version.toString()

                artifact(tasks.named("shadowJar"))
                artifact(tasks.named("sourcesJar"))

                pom {
                    name.set("${rootProject.name}-${project.name}")
                    description.set(provider { project.description })
                }

            }

        }

    }

}


tasks {

    clean {

        dependsOn(subprojects.map { "${it.path}:clean" })

    }

    build {

        dependsOn(subprojects.map { "${it.path}:build" })

    }

}

defaultTasks("clean", "build")

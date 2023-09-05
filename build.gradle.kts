import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

subprojects {
    group = "com.faforever.ice"
    version = "1.0-SNAPSHOT"

    tasks {
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    repositories {
        mavenCentral()
    }

    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        val ktlintVersion = "0.49.1"
        kotlin {
            ktlint(ktlintVersion)
        }
        kotlinGradle {
            target("*.gradle.kts")

            ktlint(ktlintVersion)
        }
    }
}

plugins {
    val kotlinVersion = "1.9.10"

    id("org.jetbrains.kotlin.jvm") version kotlinVersion apply false
    id("org.jetbrains.kotlin.kapt") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion apply false
    id("com.diffplug.spotless") version "6.21.0" apply false
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

subprojects {
    group = "com.faforever.ice"

    // Read from gradle.properties
    version = "$version"

    tasks {
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    repositories {
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        gradlePluginPortal()
    }

    apply(plugin = "com.adarshr.test-logger")
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
    val kotlinVersion = "1.9.22"

    id("org.jetbrains.kotlin.jvm") version kotlinVersion apply false
    id("org.jetbrains.kotlin.kapt") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion apply false
    id("com.diffplug.spotless") version "6.21.0" apply false
    id("com.adarshr.test-logger") version "3.2.0" apply false
    id("org.graalvm.buildtools.native") version "0.9.28" apply false
}



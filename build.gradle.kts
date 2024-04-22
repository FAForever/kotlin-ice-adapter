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
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.test.logger) apply false
    alias(libs.plugins.buildtools.native) apply false
}



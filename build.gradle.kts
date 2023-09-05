import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

allprojects {
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
}

plugins {
    val kotlinVersion = "1.9.10"

    id("org.jetbrains.kotlin.jvm") version kotlinVersion apply false
    id("org.jetbrains.kotlin.kapt") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion apply false
}

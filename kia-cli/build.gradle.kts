import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.johnrengelman.shadow)
    alias(libs.plugins.buildtools.native)
}

dependencies {
    kapt(libs.picocli.codegen)
    implementation(libs.picocli)
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jjsonrpc)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(project(":kia-lib"))
}

graalvmNative {
    toolchainDetection.set(true)
    binaries {
        named("main") {
            mainClass.set("com.faforever.ice.KiaApplication")
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    named<ShadowJar>("shadowJar") {
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "com.faforever.ice.KiaApplication",
                    "Implementation-Version" to version,
                ),
            )
        }
    }
}

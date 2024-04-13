import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.graalvm.buildtools.native")
}

dependencies {
    val picocliVersion = "4.7.5"
    val logbackVersion = "1.5.4"
    kapt("info.picocli:picocli-codegen:$picocliVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("com.github.faforever:JJsonRpc:37669e0fed")
    implementation("info.picocli:picocli:$picocliVersion")
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.4")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("ch.qos.logback:logback-core:$logbackVersion")
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
            attributes(mapOf("Main-Class" to "com.faforever.ice.KiaApplication"))
        }
    }
}

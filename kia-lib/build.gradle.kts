plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.4")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("org.jitsi:ice4j:3.0-68-gd289f12")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.ß")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("com.google.guava:guava:33.1.0-jre")
    implementation("dev.failsafe:failsafe:3.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.1")
    testImplementation("io.kotest:kotest-assertions-core:5.8.1")
    testImplementation("org.awaitility:awaitility:4.2.1")
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.slf4j:slf4j-simple:2.0.12")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

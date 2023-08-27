plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.jitsi:ice4j:3.0-62-ga947919")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("dev.failsafe:failsafe:3.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("io.mockk:mockk:1.13.7")
    testImplementation("org.slf4j:slf4j-simple:2.0.7")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
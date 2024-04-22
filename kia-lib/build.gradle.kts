plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.slf4j.api)
    implementation(libs.ice4j)
    implementation(libs.java.websocket)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.guava)
    implementation(libs.failsafe)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.awaitility)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.slf4j.simple)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

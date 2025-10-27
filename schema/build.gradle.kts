plugins {
    kotlin("jvm")
}

group = "org.apache.utlx"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Core module (for UDM)
    implementation(project(":modules:core"))

    // Kotlin stdlib
    implementation(kotlin("stdlib"))

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.5.5")
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

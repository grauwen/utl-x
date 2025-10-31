// modules/daemon/build.gradle.kts
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.22"
}

group = "org.apache.utlx"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Internal dependencies
    implementation(project(":modules:core"))
    implementation(project(":modules:analysis"))

    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Coroutines for async I/O
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // JSON-RPC serialization (Jackson)
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}

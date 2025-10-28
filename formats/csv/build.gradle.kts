plugins {
    kotlin("jvm")
    `java-library`
}

group = "org.apache.utlx"
version = "0.1.0-SNAPSHOT"

dependencies {
    // Core module dependency
    implementation(project(":modules:core"))

    // Stdlib module dependency (for regional number functions)
    implementation(project(":stdlib"))

    // Kotlin standard library
    implementation(kotlin("stdlib"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")

    // Test dependencies for examples that use other formats
    testImplementation(project(":formats:json"))
    testImplementation(project(":formats:xml"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

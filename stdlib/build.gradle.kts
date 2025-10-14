// stdlib/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

group = "org.apache.utlx"
version = "0.9.0-beta"

repositories {
    mavenCentral()
}

dependencies {
    // Core module dependency
    implementation(project(":modules:core"))
    
    // Kotlin standard library
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // Date/time support
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

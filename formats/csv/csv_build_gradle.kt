plugins {
    kotlin("jvm")
    `java-library`
}

group = "org.apache.utlx"
version = "0.1.0-SNAPSHOT"

dependencies {
    // Core module dependency
    implementation(project(":modules:core"))
    
    // Kotlin standard library
    implementation(kotlin("stdlib"))
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

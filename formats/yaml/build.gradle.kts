// formats/yaml/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

group = "org.apache.utlx.formats"
version = "0.9.0-beta"

// Repository management is handled in settings.gradle.kts
// repositories {
//     mavenCentral()
// }

dependencies {
    // Core module dependency
    implementation(project(":modules:core"))
    
    // SnakeYAML library for YAML parsing
    implementation("org.yaml:snakeyaml:2.2")
    
    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-Xjsr305=strict",
            "-opt-in=kotlin.RequiresOptIn"
        )
        jvmTarget = "17"
    }
}

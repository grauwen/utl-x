// modules/analysis/build.gradle.kts
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.20"
    id("org.jetbrains.dokka")
}

group = "org.apache.utlx.analysis"
version = "0.9.0-beta"

// Repositories managed in settings.gradle.kts

dependencies {
    // Core module dependency
    implementation(project(":modules:core"))
    
    // JSON support for JSON Schema
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // XML parsing for XSD
    implementation("javax.xml.parsers:jaxp-api:1.4.2")
    
    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
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

// Generate documentation
tasks.dokkaHtml.configure {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))
}

// Create JAR with dependencies for CLI usage
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("${project.name}-all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    from(sourceSets.main.get().output)
    
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

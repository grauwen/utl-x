plugins {
    kotlin("jvm") version "1.9.21" apply false
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "org.apache.utlx"
version = "0.1.0-SNAPSHOT"

allprojects {
    repositories {
        // mavenCentral()
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version
    
    tasks.withType<Test> {
        useJUnitPlatform()
        
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }
}

tasks.register("clean") {
    delete(layout.buildDirectory.get().asFile)
}

// Documentation generation
tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))
}

// Task to print project structure
tasks.register("projectStructure") {
    doLast {
        println("\n=== UTL-X Project Structure ===\n")
        subprojects.forEach { project ->
            println("${project.path}")
            project.tasks.names.sorted().take(5).forEach { task ->
                println("  └─ $task")
            }
        }
        println("\n" + "=".repeat(40) + "\n")
    }
}

// Aggregate test reports
tasks.register("aggregateTestReports", TestReport::class) {
    destinationDirectory.set(layout.buildDirectory.dir("reports/tests"))
    testResults.from(subprojects.map { it.tasks.withType<Test>() })
}

// ─── Convenience test tasks (granular test execution) ───

tasks.register("testStdlib") {
    dependsOn(":stdlib:test")
    group = "verification"
    description = "Run stdlib function tests only"
}

tasks.register("testCore") {
    dependsOn(":modules:core:test")
    group = "verification"
    description = "Run core language/parser tests only"
}

tasks.register("testEngine") {
    dependsOn(":modules:engine:test")
    group = "verification"
    description = "Run engine/transport/admin tests only"
}

tasks.register("testFormats") {
    dependsOn(subprojects.filter { it.path.startsWith(":formats:") }.map { "${it.path}:test" })
    group = "verification"
    description = "Run all format tests (XML, JSON, CSV, YAML, XSD, etc.)"
}

tasks.register("testFast") {
    dependsOn(":modules:core:test", ":stdlib:test")
    group = "verification"
    description = "Run core + stdlib tests (fast feedback loop)"
}

tasks.register("testModules") {
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") }.map { "${it.path}:test" })
    group = "verification"
    description = "Run all module tests (core, cli, daemon, engine, analysis)"
}

// modules/server/build.gradle.kts
plugins {
    kotlin("jvm")
    application
}

group = "org.apache.utlx"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Daemon module (LSP server)
    implementation(project(":modules:daemon"))

    // Core dependencies
    implementation(project(":modules:core"))
    implementation(project(":modules:analysis"))

    // Format parsers
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:yaml"))
    implementation(project(":formats:xsd"))
    implementation(project(":formats:jsch"))
    implementation(project(":formats:avro"))
    implementation(project(":formats:protobuf"))

    // Standard library
    implementation(project(":stdlib"))
    // implementation(project(":stdlib-security"))  // Temporarily disabled

    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Ktor for REST API
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-jackson:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("io.ktor:ktor-server-call-logging:2.3.7")

    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.ktor:ktor-server-test-host:2.3.7")
    testImplementation("io.mockk:mockk:1.13.8")
}

application {
    mainClass.set("org.apache.utlx.server.Main")

    // Set JVM args for better performance
    applicationDefaultJvmArgs = listOf(
        "-Xmx1024m",
        "-XX:+UseG1GC"
    )
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.apache.utlx.server.Main",
            "Implementation-Title" to "UTL-X Daemon Server",
            "Implementation-Version" to project.version
        )
    }

    // Create fat JAR with all dependencies
    archiveBaseName.set("utlxd")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    // Ensure dependencies are built first
    dependsOn(configurations.runtimeClasspath)
}

tasks.test {
    useJUnitPlatform()
}

// Custom tasks
tasks.register("createScripts") {
    group = "distribution"
    description = "Create shell scripts for running UTL-X daemon"

    doLast {
        val scriptsDir = File(projectDir, "scripts")
        scriptsDir.mkdirs()

        // Unix script
        File(scriptsDir, "utlxd").writeText("""
            #!/bin/bash
            SCRIPT_DIR="${'$'}( cd "${'$'}( dirname "${'$'}{BASH_SOURCE[0]}" )" && pwd )"
            JAR="${'$'}SCRIPT_DIR/../build/libs/utlxd-${project.version}.jar"

            if [ ! -f "${'$'}JAR" ]; then
                echo "Error: JAR not found at ${'$'}JAR"
                echo "Run './gradlew :modules:server:jar' first"
                exit 1
            fi

            exec java -jar "${'$'}JAR" "${'$'}@"
        """.trimIndent())

        File(scriptsDir, "utlxd").setExecutable(true)

        // Windows script
        File(scriptsDir, "utlxd.bat").writeText("""
            @echo off
            set SCRIPT_DIR=%~dp0
            set JAR=%SCRIPT_DIR%..\build\libs\utlxd-${project.version}.jar

            if not exist "%JAR%" (
                echo Error: JAR not found at %JAR%
                echo Run 'gradlew :modules:server:jar' first
                exit /b 1
            )

            java -jar "%JAR%" %*
        """.trimIndent())

        println("Created daemon scripts in $scriptsDir")
    }
}

tasks.named("build") {
    finalizedBy("createScripts")
}

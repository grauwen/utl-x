// modules/cli/build.gradle.kts
plugins {
    kotlin("jvm")
    application
    id("org.graalvm.buildtools.native") version "0.10.2"
}

group = "com.glomidco.utlx"
// version: inherited from the root build (see `subprojects` there) — one source of truth

dependencies {
    // Internal dependencies
    implementation(project(":modules:core"))
    implementation(project(":modules:analysis"))
    // Daemon removed - design-time features now in utlxd server
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:xlsx"))
    implementation(project(":formats:yaml"))
    implementation(project(":formats:xsd"))
    implementation(project(":formats:jsch"))
    implementation(project(":formats:avro"))
    implementation(project(":formats:protobuf"))
    implementation(project(":formats:odata"))
    implementation(project(":formats:osch"))
    implementation(project(":formats:tsch"))
    implementation(project(":stdlib"))
    // stdlib-security merged into stdlib/crypto/ (F11 — May 2026)
    
    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Jackson for JSON/YAML output in functions command
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")

    // JLine for REPL command history and line editing
    implementation("org.jline:jline:3.24.1")

    // Logging - suppress DEBUG messages during transformations
    implementation("ch.qos.logback:logback-classic:1.4.14")
    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.26.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

application {
    mainClass.set("com.glomidco.utlx.cli.Main")
    
    // Set JVM args for better performance
    applicationDefaultJvmArgs = listOf(
        "-Xmx512m",
        "-XX:+UseG1GC"
    )
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.glomidco.utlx.cli.Main",
            "Implementation-Title" to "UTL-X CLI",
            "Implementation-Version" to project.version
        )
    }
    
    // Create fat JAR with all dependencies
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    
    // Ensure dependencies are built first
    dependsOn(configurations.runtimeClasspath)
}

// CB08: the banner used to restate the version as a Kotlin constant and drifted three releases
// behind — a field report arrived describing "v1.4.0" printed by a cli-1.4.3.jar, which cost a
// round of diagnosis before the real bug could be isolated. Main.kt now reads
// Implementation-Version from the manifest above.
//
// No unit test can protect that. In a test run there is no jar, so `implementationVersion` is null
// and only the "dev" fallback is reachable; a test would pin the fallback, not the behaviour. The
// one thing that catches someone re-hardcoding the constant is running the artefact and reading
// what it says, so that is what this does. It is wired to `jar`, because a check nobody runs is
// theatre.
val verifyVersionBanner by tasks.registering {
    dependsOn(tasks.jar)
    description = "Assert the built jar's banner reports project.version (CB08)."
    group = "verification"

    val jarFile = tasks.jar.flatMap { it.archiveFile }
    val expected = project.version.toString()
    inputs.file(jarFile)
    outputs.upToDateWhen { false }

    doLast {
        val javaExe = "${System.getProperty("java.home")}/bin/java"
        val out = providers.exec {
            commandLine(javaExe, "-jar", jarFile.get().asFile.absolutePath, "version")
        }.standardOutput.asText.get().trim()

        if (!out.contains(expected)) {
            throw GradleException(
                "CB08: the jar's banner does not report ${expected}.\n" +
                "  expected the output to contain: ${expected}\n" +
                "  actual first line:              ${out.lineSequence().firstOrNull()}\n" +
                "The version must come from the manifest, not a literal in Main.kt."
            )
        }
        logger.lifecycle("  CB08 ok — banner reports ${expected}")
    }
}

tasks.jar { finalizedBy(verifyVersionBanner) }

// GraalVM Native Image configuration
graalvmNative {
    binaries {
        named("main") {
            imageName.set("utlx")
            mainClass.set("com.glomidco.utlx.cli.Main")
            
            buildArgs.addAll(
                "--no-fallback",
                "--strict-image-heap",
                "--initialize-at-build-time=kotlin,kotlinx,ch.qos.logback,org.slf4j,org.xml.sax,org.apache.xml.security,com.glomidco.utlx.stdlib",
                "--report-unsupported-elements-at-runtime",
                "-H:+ReportExceptionStackTraces",
                "-H:+AddAllCharsets",
                "--enable-url-protocols=http,https",
                "--allow-incomplete-classpath",
                "-march=native",
                "-R:MaxHeapSize=256m"
            )
            
            // Add verbose output for debugging
            if (System.getProperty("verbose") == "true") {
                buildArgs.add("--verbose")
            }
        }
    }
    
    // Test native image
    binaries.named("test") {
        buildArgs.add("--no-fallback")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    // B26: CLI integration tests spawn the assembled fat JAR as a subprocess. Build it first and
    // hand the path to the tests via a system property. Compute the path from `layout` (pure string,
    // no task resolution at configuration time) to avoid forcing cross-project evaluation.
    dependsOn(tasks.jar)
    systemProperty(
        "utlx.cli.jar",
        layout.buildDirectory.file("libs/cli-${project.version}.jar").get().asFile.absolutePath
    )
}

// Custom tasks
tasks.register("createScripts") {
    group = "distribution"
    description = "Create shell scripts for running UTL-X"
    
    doLast {
        val scriptsDir = File(projectDir, "scripts")
        scriptsDir.mkdirs()
        
        // Unix script
        File(scriptsDir, "utlx").writeText("""
            #!/bin/bash
            SCRIPT_DIR="${'$'}( cd "${'$'}( dirname "${'$'}{BASH_SOURCE[0]}" )" && pwd )"
            LIB_DIR="${'$'}SCRIPT_DIR/../build/libs"

            # CB14: resolve the most recently built cli-*.jar rather than naming a version. The
            # version used to be interpolated here at generation time, so the committed script
            # pinned whichever release was current when `createScripts` last ran - and after a
            # bump it silently kept launching the PREVIOUS jar, which is still sitting in
            # build/libs. Generated output must not carry a version; the manifest has it.
            JAR="${'$'}(ls -t "${'$'}LIB_DIR"/cli-*.jar 2>/dev/null | head -n 1)"

            if [ -z "${'$'}JAR" ]; then
                echo "Error: no cli JAR found in ${'$'}LIB_DIR"
                echo "Run './gradlew :modules:cli:jar' first"
                exit 1
            fi
            
            exec java -jar "${'$'}JAR" "${'$'}@"
        """.trimIndent())
        
        File(scriptsDir, "utlx").setExecutable(true)
        
        // Windows script
        File(scriptsDir, "utlx.bat").writeText("""
            @echo off
            set SCRIPT_DIR=%~dp0
            set LIB_DIR=%SCRIPT_DIR%..\build\libs

            REM CB14: newest jar, not a pinned version - see the shell script above.
            set "JAR="
            for /f "delims=" %%F in ('dir /b /o-d "%LIB_DIR%\cli-*.jar" 2^^>nul') do (
                if not defined JAR set "JAR=%LIB_DIR%\%%F"
            )

            if not defined JAR (
                echo Error: no cli JAR found in %LIB_DIR%
                echo Run 'gradlew :modules:cli:jar' first
                exit /b 1
            )
            
            java -jar "%JAR%" %*
        """.trimIndent())
        
        println("Created scripts in $scriptsDir")
    }
}

tasks.named("build") {
    finalizedBy("createScripts")
}

// Task to build everything
tasks.register("buildAll") {
    group = "build"
    description = "Build both JAR and native image"
    
    dependsOn("jar")
    dependsOn("nativeCompile")
}

// Task to install native binary locally
tasks.register<Copy>("installNative") {
    group = "distribution"
    description = "Install native binary to /usr/local/bin (requires sudo)"
    
    dependsOn("nativeCompile")
    
    from(layout.buildDirectory.file("native/nativeCompile/utlx"))
    into("/usr/local/bin")
    
    doLast {
        println("✓ Installed utlx to /usr/local/bin")
        println("Run 'utlx --version' to verify")
    }
}

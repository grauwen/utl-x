// modules/cli/build.gradle.kts
plugins {
    kotlin("jvm")
    application
    id("org.graalvm.buildtools.native") version "0.10.2"
}

group = "org.apache.utlx"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Internal dependencies
    implementation(project(":modules:core"))
    implementation(project(":modules:analysis"))
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:yaml"))
    implementation(project(":stdlib"))
    // implementation(project(":stdlib-security"))  // Temporarily disabled
    
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

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

application {
    mainClass.set("org.apache.utlx.cli.Main")
    
    // Set JVM args for better performance
    applicationDefaultJvmArgs = listOf(
        "-Xmx512m",
        "-XX:+UseG1GC"
    )
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.apache.utlx.cli.Main",
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

// GraalVM Native Image configuration
graalvmNative {
    binaries {
        named("main") {
            imageName.set("utlx")
            mainClass.set("org.apache.utlx.cli.Main")
            
            buildArgs.addAll(
                "--no-fallback",
                "--initialize-at-build-time=kotlin,kotlinx",
                "--report-unsupported-elements-at-runtime",
                "-H:+ReportExceptionStackTraces",
                "-H:+AddAllCharsets",
                "--enable-url-protocols=http,https",
                "--allow-incomplete-classpath"
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

tasks.test {
    useJUnitPlatform()
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
            JAR="${'$'}SCRIPT_DIR/../build/libs/cli-${project.version}.jar"
            
            if [ ! -f "${'$'}JAR" ]; then
                echo "Error: JAR not found at ${'$'}JAR"
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
            set JAR=%SCRIPT_DIR%..\build\libs\cli-${project.version}.jar
            
            if not exist "%JAR%" (
                echo Error: JAR not found at %JAR%
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

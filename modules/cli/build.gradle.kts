plugins {
    kotlin("jvm")
    application
    id("org.graalvm.buildtools.native") version "0.10.2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.apache.utlx"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Internal dependencies
    implementation(project(":modules:core"))
    implementation(project(":modules:jvm"))
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:yaml"))
    implementation(project(":stdlib"))

    // CLI framework
    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
}

application {
    mainClass.set("org.apache.utlx.cli.MainKt")
}

// GraalVM Native Image Configuration
graalvmNative {
    binaries {
        named("main") {
            // Output binary name
            imageName.set("utlx")
            
            // Main class
            mainClass.set("org.apache.utlx.cli.MainKt")
            
            // Build arguments for optimization
            buildArgs.addAll(
                "--no-fallback",                    // No fallback to JVM
                "--install-exit-handlers",          // Better error messages
                "-H:+ReportExceptionStackTraces",   // Debug info
                "-H:+AddAllCharsets",               // Support all encodings
                "--enable-url-protocols=http,https", // Network support if needed
                "--initialize-at-build-time=kotlin", // Kotlin runtime at build time
                "--initialize-at-build-time=org.slf4j", // Logging at build time
                "-H:ReflectionConfigurationFiles=src/main/resources/META-INF/native-image/reflect-config.json",
                "-H:ResourceConfigurationFiles=src/main/resources/META-INF/native-image/resource-config.json",
                "-H:DynamicProxyConfigurationFiles=src/main/resources/META-INF/native-image/proxy-config.json",
                "-H:SerializationConfigurationFiles=src/main/resources/META-INF/native-image/serialization-config.json",
                
                // Optimization flags
                "-O3",                              // Maximum optimization
                "--gc=G1",                          // G1 garbage collector
                "-march=native"                     // Optimize for current CPU
            )
            
            // JVM arguments during build
            jvmArgs.addAll(
                "-Xmx4g",                           // More memory for build
                "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.configure=ALL-UNNAMED"
            )
            
            // Resource configuration
            resources {
                includedPatterns.addAll(
                    "org/apache/utlx/**/*.properties",
                    "org/apache/utlx/**/*.utlx",
                    "META-INF/services/**"
                )
            }
        }
        
        // Optional: Create a shared library version
        create("shared") {
            sharedLibrary.set(true)
            imageName.set("libutlx")
            mainClass.set("org.apache.utlx.cli.MainKt")
        }
    }
    
    // Agent configuration for automatic metadata generation
    agent {
        enabled.set(true)
        defaultMode.set("standard")
        modes {
            standard {
                // Run tests with agent to generate config
            }
        }
    }
}

tasks {
    // Test task for GraalVM agent
    test {
        useJUnitPlatform()
        
        // Enable GraalVM agent during tests
        if (project.hasProperty("agent")) {
            jvmArgs(
                "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image"
            )
        }
    }
    
    // Shadow JAR for fallback
    shadowJar {
        archiveClassifier.set("all")
        manifest {
            attributes["Main-Class"] = "org.apache.utlx.cli.MainKt"
        }
        mergeServiceFiles()
    }
}

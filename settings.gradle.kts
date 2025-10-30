rootProject.name = "utl-x"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Move repositories here (CRITICAL FIX)
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

// Future modules (commented out until implemented)
// include(":modules:jvm")
// include(":modules:javascript")
// include(":modules:native")
// include(":stdlib")
// include(":formats:yaml")
// include(":formats:plugin")
// include(":tools:vscode-extension")
// include(":tools:intellij-plugin")
// include(":tools:maven-plugin")
// include(":tools:gradle-plugin")
// include(":tools:benchmarks")



// Core modules
include("modules:core")
//include("modules:jvm")
//include("modules:javascript")
//include("modules:native")
include("modules:cli")
//include("modules:analysis")  // Temporarily disabled - test compilation errors


// Format parsers/serializers
include("formats:xml")
include("formats:json")
include("formats:csv")
include("formats:yaml")
include("formats:xsd")
include("formats:jsch")
include("formats:avro")
include("formats:protobuf")
//include("formats:plugin")

// Standard library
include("stdlib")
include("stdlib-security")

// Schema utilities (USDL implementation)
include("schema")

// Development tools
//include("tools:vscode-extension")
//include("tools:intellij-plugin")
//include("tools:maven-plugin")
//include("tools:gradle-plugin")
//include("tools:benchmarks")

// Enable Gradle build cache
buildCache {
    local {
        isEnabled = true
        directory = file("${rootDir}/.gradle/build-cache")
    }
}

// Plugin management
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    
    plugins {
        kotlin("jvm") version "1.9.22"
        id("org.graalvm.buildtools.native") version "0.10.2"
    }
}

// Dependency resolution management is already defined at the top of this file

rootProject.name = "utl-x"

// Core modules
include("modules:core")
include("modules:jvm")
include("modules:javascript")
include("modules:native")
include("modules:cli")

// Format parsers/serializers
include("formats:xml")
include("formats:json")
include("formats:csv")
include("formats:yaml")
include("formats:plugin")

// Standard library
include("stdlib")

// Development tools
include("tools:vscode-extension")
include("tools:intellij-plugin")
include("tools:maven-plugin")
include("tools:gradle-plugin")
include("tools:benchmarks")

// Enable Gradle build cache
buildCache {
    local {
        isEnabled = true
        directory = file("${rootDir}/.gradle/build-cache")
    }
}

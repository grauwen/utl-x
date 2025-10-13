#!/bin/bash
# Initialize module build files

echo "🔧 Initializing module build configurations..."

# Function to create build.gradle.kts for a module
create_build_file() {
    local module_path=$1
    local module_name=$2
    local dependencies=$3
    
    cat > "$module_path/build.gradle.kts" << EOF
plugins {
    kotlin("jvm")
}

dependencies {
$dependencies
}

tasks.test {
    useJUnitPlatform()
}
EOF
    echo "✅ Created build file for $module_name"
}

# Core module
create_build_file "modules/core" "core" "    // No additional dependencies"

# JVM module
create_build_file "modules/jvm" "jvm" "    implementation(project(\":modules:core\"))
    implementation(project(\":stdlib\"))
    implementation(\"org.ow2.asm:asm:9.6\")"

# CLI module
create_build_file "modules/cli" "cli" "    implementation(project(\":modules:core\"))
    implementation(project(\":modules:jvm\"))
    implementation(\"com.github.ajalt.clikt:clikt:4.2.1\")
    implementation(\"org.fusesource.jansi:jansi:2.4.0\")"

# Format modules
for format in xml json csv yaml; do
    create_build_file "formats/$format" "$format" "    implementation(project(\":modules:core\"))"
done

# Plugin module
create_build_file "formats/plugin" "plugin" "    implementation(project(\":modules:core\"))"

# Stdlib
create_build_file "stdlib" "stdlib" "    implementation(project(\":modules:core\"))"

echo "🎉 Module initialization complete!"

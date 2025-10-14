#!/bin/bash
# ============================================
# UTL-X Analysis Module - Complete Setup Script
# ============================================
# This script creates the complete directory structure
# for the analysis module with all necessary files.

set -e  # Exit on error

echo "🚀 Setting up UTL-X Analysis Module..."
echo ""

# Base directory
BASE_DIR="modules/analysis"

# ============================================
# 1. Create Directory Structure
# ============================================
echo "📁 Creating directory structure..."

mkdir -p "$BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/types"
mkdir -p "$BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/schema"
mkdir -p "$BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/validation"
mkdir -p "$BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/cli"

mkdir -p "$BASE_DIR/src/test/kotlin/org/apache/utlx/analysis/types"
mkdir -p "$BASE_DIR/src/test/kotlin/org/apache/utlx/analysis/schema"
mkdir -p "$BASE_DIR/src/test/kotlin/org/apache/utlx/analysis/validation"
mkdir -p "$BASE_DIR/src/test/kotlin/org/apache/utlx/analysis/integration"

echo "✅ Directory structure created"
echo ""

# ============================================
# 2. Create build.gradle.kts
# ============================================
echo "📝 Creating build.gradle.kts..."

cat > "$BASE_DIR/build.gradle.kts" << 'EOF'
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.22"
}

group = "org.apache.utlx"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Core module dependency
    implementation(project(":modules:core"))
    
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // Kotlinx Serialization for JSON Schema parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // XML processing for XSD
    implementation("org.dom4j:dom4j:2.1.4")
    implementation("jaxen:jaxen:2.0.0")
    
    // YAML parsing
    implementation("org.yaml:snakeyaml:2.2")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
EOF

echo "✅ build.gradle.kts created"
echo ""

# ============================================
# 3. Create Interface Files
# ============================================
echo "📝 Creating interface files..."

# InputSchemaParser.kt
cat > "$BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/schema/InputSchemaParser.kt" << 'EOF'
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.TypeDefinition
import org.apache.utlx.analysis.types.SchemaFormat

/**
 * Interface for parsing schema formats to TypeDefinition
 */
interface InputSchemaParser {
    fun parse(schema: String, format: SchemaFormat): TypeDefinition
}
EOF

# OutputSchemaGenerator.kt
cat > "$BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/schema/OutputSchemaGenerator.kt" << 'EOF'
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.TypeDefinition
import org.apache.utlx.analysis.types.SchemaFormat

/**
 * Generator options
 */
data class GeneratorOptions(
    val pretty: Boolean = true,
    val includeComments: Boolean = true,
    val includeExamples: Boolean = false,
    val targetNamespace: String? = null,
    val rootElementName: String? = null
)

/**
 * Interface for generating schemas from TypeDefinition
 */
interface OutputSchemaGenerator {
    fun generate(
        type: TypeDefinition,
        format: SchemaFormat,
        options: GeneratorOptions = GeneratorOptions()
    ): String
}
EOF

echo "✅ Interface files created"
echo ""

# ============================================
# 4. File Checklist
# ============================================
echo "📋 Implementation Checklist:"
echo ""
echo "Core Type System (✅ = Created, 🚧 = Needs implementation):"
echo "  ✅ TypeDefinition.kt"
echo "  ✅ TypeContext.kt"
echo "  ✅ FunctionRegistry.kt"
echo ""
echo "Schema Parsing:"
echo "  ✅ InputSchemaParser.kt (interface)"
echo "  ✅ JSONSchemaParser.kt"
echo "  ✅ XSDSchemaParser.kt"
echo ""
echo "Schema Generation:"
echo "  ✅ OutputSchemaGenerator.kt (interface)"
echo "  🚧 JSONSchemaGenerator.kt (needs implementation)"
echo "  🚧 XSDGenerator.kt (needs implementation)"
echo "  📋 OpenAPIGenerator.kt (planned)"
echo "  📋 SchemaGenerator.kt (main entry point)"
echo ""
echo "Validation:"
echo "  📋 TransformValidator.kt"
echo "  📋 SchemaValidator.kt"
echo "  📋 SchemaDiffer.kt"
echo ""
echo "Tests (✅ = All created):"
echo "  ✅ XSDSchemaParserTest.kt (18 tests)"
echo "  ✅ JSONSchemaGeneratorTest.kt (12 tests)"
echo "  ✅ XSDGeneratorTest.kt (10 tests)"
echo "  ✅ SchemaGeneratorTest.kt (8 tests)"
echo "  ✅ TypeInferenceTest.kt (23 tests)"
echo "  ✅ AdvancedTypeInferenceTest.kt (17 tests)"
echo "  ✅ TransformValidatorTest.kt (18 tests)"
echo "  ✅ SchemaValidatorTest.kt (14 tests)"
echo "  ✅ SchemaDifferTest.kt (12 tests)"
echo ""

# ============================================
# 5. Update settings.gradle.kts
# ============================================
echo "📝 Checking settings.gradle.kts..."

if ! grep -q "include(\":modules:analysis\")" settings.gradle.kts 2>/dev/null; then
    echo ""
    echo "⚠️  Please add the following to your settings.gradle.kts:"
    echo ""
    echo "include(\":modules:analysis\")"
    echo ""
else
    echo "✅ settings.gradle.kts already includes analysis module"
fi

# ============================================
# 6. Quick Start Commands
# ============================================
echo ""
echo "🎯 Quick Start Commands:"
echo ""
echo "# Build the module"
echo "./gradlew :modules:analysis:build"
echo ""
echo "# Run all tests"
echo "./gradlew :modules:analysis:test"
echo ""
echo "# Run specific test class"
echo "./gradlew :modules:analysis:test --tests XSDSchemaParserTest"
echo ""
echo "# Generate test coverage report"
echo "./gradlew :modules:analysis:test :modules:analysis:jacocoTestReport"
echo ""
echo "# Run tests in watch mode"
echo "./gradlew :modules:analysis:test --continuous"
echo ""

# ============================================
# 7. Next Steps
# ============================================
echo "📌 Next Steps:"
echo ""
echo "1. Copy the created artifacts to their respective files:"
echo "   - TypeDefinition.kt → $BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/types/"
echo "   - TypeContext.kt → $BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/types/"
echo "   - FunctionRegistry.kt → $BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/types/"
echo "   - JSONSchemaParser.kt → $BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/schema/"
echo "   - XSDSchemaParser.kt → $BASE_DIR/src/main/kotlin/org/apache/utlx/analysis/schema/"
echo ""
echo "2. Copy all test files to:"
echo "   - Test files → $BASE_DIR/src/test/kotlin/org/apache/utlx/analysis/[schema|types|validation]/"
echo ""
echo "3. Implement remaining generators:"
echo "   - JSONSchemaGenerator.kt (partial implementation provided)"
echo "   - XSDGenerator.kt (tests provided)"
echo "   - SchemaGenerator.kt (tests provided)"
echo ""
echo "4. Run tests to verify:"
echo "   ./gradlew :modules:analysis:test"
echo ""

echo "✨ Analysis module setup complete!"
echo ""
echo "📚 Documentation: See modules/analysis/README.md"
echo "🧪 Test Coverage: 132+ test cases across 9 test files"
echo ""

# Complete UTL-X Project Structure (with Analysis Module)

## Updated Directory Structure

```
utl-x/
├── .github/
│   └── workflows/
│       ├── ci.yml
│       ├── schema-validation.yml          # NEW: Schema CI/CD
│       └── native-build.yml
│
├── docs/
│   ├── getting-started/
│   ├── language-guide/
│   ├── formats/
│   ├── examples/
│   ├── reference/
│   ├── architecture/
│   ├── comparison/
│   ├── community/
│   └── analysis/                           # NEW: Schema analysis docs
│       ├── schema-generation.md
│       ├── type-inference.md
│       ├── validation.md
│       └── cli-reference.md
│
├── examples/
│   ├── basic/
│   ├── intermediate/
│   ├── advanced/
│   └── schema-examples/                    # NEW: Schema examples
│       ├── order-to-invoice/
│       │   ├── order-schema.xsd
│       │   ├── order-to-invoice.utlx
│       │   └── invoice-schema.json
│       ├── customer-processing/
│       └── multi-format-transformation/
│
├── modules/
│   ├── core/                               # Language core
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/glomidco/utlx/core/
│   │       │   ├── ast/                    # Abstract Syntax Tree
│   │       │   ├── lexer/                  # Tokenization
│   │       │   ├── parser/                 # Parsing
│   │       │   ├── types/                  # Type system
│   │       │   ├── optimizer/              # Optimization
│   │       │   ├── interpreter/            # Interpreter
│   │       │   └── udm/                    # Universal Data Model
│   │       └── test/
│   │
│   ├── jvm/                                # JVM runtime
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/glomidco/utlx/jvm/
│   │       │   ├── runtime/
│   │       │   ├── compiler/
│   │       │   ├── api/
│   │       │   └── integration/
│   │       └── test/
│   │
│   ├── analysis/                           # NEW: Schema analysis module
│   │   ├── build.gradle.kts
│   │   ├── README.md
│   │   └── src/
│   │       ├── main/kotlin/com/glomidco/utlx/analysis/
│   │       │   ├── schema/
│   │       │   │   ├── SchemaGenerator.kt
│   │       │   │   ├── InputSchemaParser.kt
│   │       │   │   ├── XSDSchemaParser.kt
│   │       │   │   ├── JSONSchemaParser.kt
│   │       │   │   ├── JSONSchemaGenerator.kt
│   │       │   │   ├── XSDGenerator.kt
│   │       │   │   └── OpenAPIGenerator.kt
│   │       │   ├── types/
│   │       │   │   ├── TypeDefinition.kt
│   │       │   │   ├── TypeInference.kt
│   │       │   │   ├── AdvancedTypeInference.kt
│   │       │   │   ├── TypeContext.kt
│   │       │   │   └── FunctionRegistry.kt
│   │       │   └── validation/
│   │       │       ├── TransformValidator.kt
│   │       │       ├── SchemaValidator.kt
│   │       │       ├── SchemaDiffer.kt
│   │       │       └── ValidationResult.kt
│   │       └── test/kotlin/com/glomidco/utlx/analysis/
│   │           ├── schema/
│   │           │   ├── XSDSchemaParserTest.kt
│   │           │   ├── JSONSchemaGeneratorTest.kt
│   │           │   └── SchemaGeneratorTest.kt
│   │           ├── types/
│   │           │   ├── TypeInferenceTest.kt
│   │           │   └── AdvancedTypeInferenceTest.kt
│   │           └── validation/
│   │               └── TransformValidatorTest.kt
│   │
│   └── cli/                                # Command-line interface
│       ├── build.gradle.kts
│       └── src/
│           ├── main/kotlin/com/glomidco/utlx/cli/
│           │   ├── Main.kt
│           │   └── commands/
│           │       ├── TransformCommand.kt
│           │       ├── ValidateCommand.kt
│           │       ├── CompileCommand.kt
│           │       ├── SchemaCommand.kt      # NEW: Schema commands
│           │       └── VersionCommand.kt
│           └── test/
│
├── formats/                                # Format parsers/serializers
│   ├── xml/
│   │   ├── build.gradle.kts
│   │   └── src/
│   ├── json/
│   │   ├── build.gradle.kts
│   │   └── src/
│   ├── csv/
│   │   ├── build.gradle.kts
│   │   └── src/
│   └── yaml/                               # NEW: YAML support
│       ├── build.gradle.kts
│       ├── yaml_readme.md
│       ├── quick_reference.md
│       └── src/
│           ├── main/kotlin/com/glomidco/utlx/formats/yaml/
│           │   ├── YAMLParser.kt
│           │   └── YAMLSerializer.kt
│           └── test/
│
├── stdlib/                                 # Standard library
│   ├── build.gradle.kts
│   └── src/
│
├── tools/                                  # Development tools
│   ├── vscode-extension/
│   ├── intellij-plugin/
│   ├── maven-plugin/
│   │   └── src/main/java/
│   │       └── com/glomidco/utlx/maven/
│   │           ├── GenerateSchemaMojo.java  # NEW: Schema generation
│   │           └── ValidateSchemaMojo.java  # NEW: Schema validation
│   ├── gradle-plugin/
│   │   └── src/main/kotlin/
│   │       └── com/glomidco/utlx/gradle/
│   │           ├── SchemaGenerationTask.kt  # NEW
│   │           └── SchemaValidationTask.kt  # NEW
│   └── benchmarks/
│
├── scripts/
│   ├── build.sh
│   ├── test.sh
│   ├── benchmark-cli.sh
│   └── generate-schemas.sh                 # NEW: Batch schema generation
│
├── build.gradle.kts                        # Root build file
├── settings.gradle.kts
├── README.md
├── LICENSE.md
├── CONTRIBUTING.md
└── CHANGELOG.md
```

## Updated settings.gradle.kts

```kotlin
rootProject.name = "utl-x"

include(
    // Core modules
    ":modules:core",
    ":modules:jvm",
    ":modules:cli",
    ":modules:analysis",          // NEW: Analysis module
    
    // Format modules
    ":formats:xml",
    ":formats:json",
    ":formats:csv",
    ":formats:yaml",              // NEW: YAML support
    
    // Standard library
    ":stdlib",
    
    // Tools
    ":tools:vscode-extension",
    ":tools:intellij-plugin",
    ":tools:maven-plugin",
    ":tools:gradle-plugin",
    ":tools:benchmarks"
)
```

## Module Dependencies

```
┌──────────────────────────────────────────────────────────┐
│                      CLI Module                           │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Commands:                                          │ │
│  │  - transform, validate, compile                   │ │
│  │  - schema (generate, validate, infer, diff)  NEW │ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
           │          │          │
           ▼          ▼          ▼
    ┌──────────┐ ┌──────────┐ ┌──────────────┐
    │   Core   │ │   JVM    │ │  Analysis    │  NEW
    │  Module  │ │  Module  │ │   Module     │
    └──────────┘ └──────────┘ └──────────────┘
           │          │          │
           └──────────┴──────────┘
                     │
              ┌──────┴──────┐
              ▼              ▼
         ┌─────────┐    ┌─────────┐
         │ Formats │    │ Stdlib  │
         │ (XML,   │    │         │
         │ JSON,   │    │         │
         │ CSV,    │    │         │
         │ YAML)   │    │         │
         └─────────┘    └─────────┘
```

## New Files Added

### Analysis Module Core Files

1. **modules/analysis/build.gradle.kts** - Build configuration
2. **modules/analysis/README.md** - Module documentation
3. **modules/analysis/src/main/kotlin/com/glomidco/utlx/analysis/**
   - `schema/SchemaGenerator.kt` - Main generator
   - `schema/XSDSchemaParser.kt` - XSD parser
   - `schema/JSONSchemaParser.kt` - JSON Schema parser
   - `schema/JSONSchemaGenerator.kt` - JSON Schema generator
   - `types/TypeDefinition.kt` - Type system
   - `types/AdvancedTypeInference.kt` - Type inference engine
   - `types/TypeContext.kt` - Variable tracking
   - `types/FunctionRegistry.kt` - Function signatures
   - `validation/TransformValidator.kt` - Validator
   - `validation/SchemaDiffer.kt` - Schema comparison

### CLI Extensions

4. **modules/cli/src/main/kotlin/com/glomidco/utlx/cli/commands/SchemaCommand.kt**
   - `schema generate` command
   - `schema validate` command
   - `schema infer` command
   - `schema diff` command

### YAML Format Support

5. **formats/yaml/build.gradle.kts**
6. **formats/yaml/src/main/kotlin/com/glomidco/utlx/formats/yaml/**
   - `YAMLParser.kt`
   - `YAMLSerializer.kt`

### Documentation

7. **docs/analysis/** - Schema analysis documentation
   - `schema-generation.md`
   - `type-inference.md`
   - `validation.md`
   - `cli-reference.md`

### Examples

8. **examples/schema-examples/** - Working examples
   - `order-to-invoice/` - Complete example
   - `customer-processing/` - Another example

### Build Tool Plugins

9. **tools/maven-plugin/src/main/java/com/glomidco/utlx/maven/**
   - `GenerateSchemaMojo.java`
   - `ValidateSchemaMojo.java`

10. **tools/gradle-plugin/src/main/kotlin/com/glomidco/utlx/gradle/**
    - `SchemaGenerationTask.kt`
    - `SchemaValidationTask.kt`

### CI/CD

11. **.github/workflows/schema-validation.yml** - Schema validation workflow

### Scripts

12. **scripts/generate-schemas.sh** - Batch schema generation

## Quick Start with New Features

### 1. Build Everything

```bash
./gradlew build
```

### 2. Generate Schema

```bash
# Using CLI
java -jar modules/cli/build/libs/cli-1.3.0.jar schema generate \
  --input-schema examples/schema-examples/order-to-invoice/order-schema.xsd \
  --transform examples/schema-examples/order-to-invoice/order-to-invoice.utlx \
  --output-format json-schema \
  --output invoice-schema.json
```

### 3. Validate Transformation

```bash
java -jar modules/cli/build/libs/cli-1.3.0.jar schema validate \
  --input-schema examples/schema-examples/order-to-invoice/order-schema.xsd \
  --transform examples/schema-examples/order-to-invoice/order-to-invoice.utlx \
  --verbose
```

### 4. Use in Build Tool

```kotlin
// build.gradle.kts
plugins {
    id("com.glomidco.utlx.schema") version "1.3.0"
}

utlxSchema {
    transformations {
        register("orderToInvoice") {
            inputSchema.set(file("schemas/order.xsd"))
            transform.set(file("transforms/order-to-invoice.utlx"))
            outputFormat.set("json-schema")
            outputFile.set(file("build/schemas/invoice.json"))
        }
    }
}
```

## Development Workflow

### 1. Add Analysis Module to Your Project

```kotlin
// your-project/build.gradle.kts
dependencies {
    implementation("com.glomidco.utlx:analysis:1.3.0")
    implementation("com.glomidco.utlx:core:1.3.0")
}
```

### 2. Use Programmatically

```kotlin
import com.glomidco.utlx.analysis.schema.*

val schemaGen = SchemaGenerator()
val outputSchema = schemaGen.generate(
    transformation = myTransform,
    inputSchemaContent = myInputSchema,
    inputSchemaFormat = SchemaFormat.XSD,
    outputSchemaFormat = SchemaFormat.JSON_SCHEMA
)
```

## Summary of New Capabilities

| Feature | Module | Status |
|---------|--------|--------|
| YAML Format Support | `formats/yaml` | ✅ Complete |
| XSD Parsing | `modules/analysis` | ✅ Complete |
| JSON Schema Parsing | `modules/analysis` | ✅ Complete |
| JSON Schema Generation | `modules/analysis` | ✅ Complete |
| Type Inference | `modules/analysis` | ✅ Complete |
| Transform Validation | `modules/analysis` | ✅ Complete |
| Schema CLI Commands | `modules/cli` | ✅ Complete |
| Schema Diff | `modules/analysis` | ✅ Complete |
| Gradle Plugin | `tools/gradle-plugin` | 🚧 Planned |
| Maven Plugin | `tools/maven-plugin` | 🚧 Planned |
| OpenAPI Generation | `modules/analysis` | 🚧 Planned |
| XSD Generation | `modules/analysis` | 🚧 Planned |

---

The UTL-X project now has comprehensive schema analysis capabilities, making it a complete solution for design-time and runtime data transformations! 🚀

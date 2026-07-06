# UTL-X Analysis Module Study: Schema Inference and External Libraries

**Date**: 2025-10-30
**Author**: Analysis of UTL-X architecture and design decisions
**Purpose**: Evaluate whether to use external libraries for schema inference functionality

---

## Executive Summary

This document analyzes whether UTL-X should use external Java/Kotlin libraries (XMLBeans, json-schema-inferrer, etc.) for schema inference functionality, or implement custom solutions.

**Key Findings**:
- ✅ **JSON Schema**: Use external library (`json-schema-inferrer`) - high value, manageable size
- ✅ **Avro Schema**: Use Avro's built-in reflection with custom UDM bridge
- ❌ **XSD**: Skip or implement basic custom only - low ROI, unreliable results

---

## Table of Contents

1. [Current UTL-X Dependency Philosophy](#current-utlx-dependency-philosophy)
2. [Schema Inference vs Schema Transformation](#schema-inference-vs-schema-transformation)
3. [XML Schema (XSD) Inference Options](#xml-schema-xsd-inference-options)
4. [JSON Schema Inference Options](#json-schema-inference-options)
5. [Avro Schema Inference Options](#avro-schema-inference-options)
6. [Architecture Proposal](#architecture-proposal)
7. [Recommendations Summary](#recommendations-summary)
8. [Implementation Priority](#implementation-priority)

---

## Current UTL-X Dependency Philosophy

UTL-X follows a **hybrid approach** to external dependencies:

### External Libraries Currently Used

| Library | Version | Purpose | Module |
|---------|---------|---------|--------|
| **SnakeYAML** | 2.2 | YAML data parsing | `formats:yaml` |
| **Apache Avro** | 1.11.3 | Avro schema validation & binary encoding | `formats:avro` |
| **Jackson** | 2.16.1 | JSON/YAML output formatting in CLI | `modules:cli` |
| **javax.xml** | - | XML parsing (JDK built-in) | `formats:xml` |

### Custom Implementations

- **XSD Parser** - Custom implementation on top of XML parser
- **JSON Schema (JSCH) Parser** - Custom implementation on top of JSON parser
- **Protobuf Schema Parser** - Custom implementation
- **All data parsers integrate with UDM** - Custom bridge code for each format

### Design Philosophy

The current approach suggests:
- **Use external libraries** when they provide significant value for data format handling
- **Custom implementations** for schema formats to ensure UDM integration
- **Leverage standard libraries** (Avro, SnakeYAML) for validation and encoding

---

## Schema Inference vs Schema Transformation

It's critical to distinguish between two different capabilities:

### Schema Transformation (Currently in Analysis Module)

**Input**: Existing schema in one format
**Output**: Schema in a different format
**Example**: XSD → JSON Schema, Avro → Protobuf

```
order.xsd  ──[transform]──>  order.schema.json
```

**Use Cases**:
- Converting legacy XSD to modern JSON Schema
- Generating Protobuf from Avro for gRPC services
- Creating multiple schema formats from single source

### Schema Inference (New Capability Being Considered)

**Input**: Actual data instances
**Output**: Schema that describes the data
**Example**: XML data → XSD, JSON data → JSON Schema

```
order.json  ──[infer]──>  order.schema.json
order.xml   ──[infer]──>  order.xsd
```

**Use Cases**:
- Documenting data structure from examples
- Creating initial schema drafts
- Validating data consistency across instances

### Fundamental Challenge with Inference

Schema inference from a single data instance has inherent limitations:

```json
// From this data instance:
{
  "person": {
    "name": "John",
    "age": 30
  }
}

// You CANNOT reliably infer:
// - Is "age" always present? (minOccurs in XSD, required in JSON Schema)
// - Can there be multiple "person" entries? (maxOccurs in XSD)
// - Should "age" be an integer or just happened to be in this instance?
// - Are there other possible fields not shown in this single instance?
```

**Conclusion**: Schema inference is inherently **imprecise** and requires **multiple instances** or **heuristics** for quality results.

---

## XML Schema (XSD) Inference Options

### Available Java/Kotlin Libraries

#### Option 1: Apache XMLBeans

**Overview**: Mature XML-to-Java binding framework with instance-to-schema tools.

| Aspect | Details |
|--------|---------|
| **Pros** | ✅ Mature, battle-tested (20+ years)<br>✅ `inst2xsd` tool for inference<br>✅ Full XSD 1.0 support<br>✅ Handles complex types, namespaces |
| **Cons** | ❌ Heavy (5+ MB JAR)<br>❌ Code generation focused (not data transformation)<br>❌ Complex API for simple use case<br>❌ Designed for Java binding, not schema inference |
| **License** | Apache 2.0 ✅ |
| **Maintenance** | ⚠️ Limited recent activity |
| **Size Impact** | 🔴 Significant (5+ MB) |

**Example Usage**:
```kotlin
// Would need to wrap inst2xsd command-line tool
// or use complex XmlBeans API
val xmlInstances = arrayOf(File("order1.xml"), File("order2.xml"))
val xsdOutput = File("order.xsd")

Inst2XsdOptions().apply {
    setDesign(Inst2XsdOptions.DESIGN_VENETIAN_BLIND)
    setOutputFile(xsdOutput)
}
Inst2Xsd.inst2xsd(xmlInstances, options)
```

#### Option 2: Trang

**Overview**: Multi-format schema converter and inference tool by James Clark.

| Aspect | Details |
|--------|---------|
| **Pros** | ✅ Excellent inference quality<br>✅ Multi-format (XSD, RELAX NG, DTD)<br>✅ Used by industry (Oxygen XML)<br>✅ Handles edge cases well |
| **Cons** | ❌ Standalone tool, not a library<br>❌ Would need process execution or JNI<br>❌ Harder to integrate |
| **License** | BSD 3-Clause ✅ |
| **Maintenance** | ⚠️ Stable but minimal updates |
| **Integration** | 🔴 Complex (external process) |

**Example Usage**:
```kotlin
// Would need to execute as external process
val process = ProcessBuilder(
    "java", "-jar", "trang.jar",
    "order1.xml", "order2.xml", "order.xsd"
).start()
```

#### Option 3: Custom Implementation

**Overview**: Build schema inference from UDM analysis.

| Aspect | Details |
|--------|---------|
| **Pros** | ✅ Full control over logic<br>✅ Native UDM integration<br>✅ No external dependencies<br>✅ Can optimize for UTL-X use cases |
| **Cons** | ❌ Significant development time<br>❌ Edge cases (namespaces, mixed content, etc.)<br>❌ Ongoing maintenance burden<br>❌ Difficult to handle XSD complexity |
| **License** | N/A (own code) |
| **Quality** | ⚠️ Likely inferior to mature tools |
| **Size Impact** | ✅ Minimal |

**Example Implementation**:
```kotlin
fun inferXsdFromUdm(data: UDM.Object): String {
    val rootElement = data.properties.keys.first()
    val elements = data.properties[rootElement]?.asObject()?.properties ?: emptyMap()

    val elementDefs = elements.map { (name, value) ->
        val xsdType = when (value) {
            is UDM.Scalar -> inferXsdType(value.value)
            is UDM.Array -> "xs:string" // Oversimplified
            is UDM.Object -> "ComplexType_$name" // Would need recursion
            else -> "xs:string"
        }
        """<xs:element name="$name" type="$xsdType"/>"""
    }.joinToString("\n    ")

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:element name="$rootElement">
            <xs:complexType>
              <xs:sequence>
                $elementDefs
              </xs:sequence>
            </xs:complexType>
          </xs:element>
        </xs:schema>
    """.trimIndent()
}

private fun inferXsdType(value: Any?): String = when {
    value is Number -> "xs:decimal"
    value is Boolean -> "xs:boolean"
    value.toString().matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> "xs:date"
    else -> "xs:string"
}
```

### XSD Inference Recommendation

**❌ DON'T Implement XSD Inference** (or implement minimal version only)

**Rationale**:

1. **Low ROI**: XSD inference is notoriously unreliable from single instances
2. **Heavy Dependencies**: Quality tools like XMLBeans add 5+ MB
3. **Limited Use Case**: Modern systems prefer JSON Schema over XSD
4. **Complexity**: XSD has enormous feature set (substitution groups, abstract types, etc.)
5. **Better Alternative**: Focus on **schema transformation** (XSD → JSON Schema) instead

**Alternative Approach**:

```
User Workflow (Recommended):
1. Use Trang externally: xml → xsd (if needed)
2. Use UTL-X: xsd → json-schema (transformation)
3. Use UTL-X: data validation against schemas
```

---

## JSON Schema Inference Options

### Available Java/Kotlin Libraries

#### Option 1: json-schema-inferrer (Recommended)

**Overview**: Dedicated library for inferring JSON Schema from JSON data.

| Aspect | Details |
|--------|---------|
| **Pros** | ✅ Actively maintained (2024 releases)<br>✅ Draft 6/7/2019-09/2020-12 support<br>✅ Configurable inference strategies<br>✅ Handles multiple instances (merging)<br>✅ Type detection and narrowing |
| **Cons** | ❌ ~200KB + dependencies (~500KB total)<br>❌ Requires Jackson (but CLI already uses it) |
| **License** | Apache 2.0 ✅ |
| **Group/Artifact** | `com.github.victools:jsonschema-generator` |
| **Maintenance** | ✅ Active (regular releases) |
| **Quality** | ✅ Production-ready, well-tested |
| **Size Impact** | 🟡 Moderate (~500KB total) |

**Example Usage**:
```kotlin
import com.github.victools.jsonschema.generator.*

dependencies {
    implementation("com.github.victools:jsonschema-generator:4.33.1")
}

fun inferJsonSchema(jsonData: UDM, schemaVersion: SchemaVersion = SchemaVersion.DRAFT_2020_12): UDM {
    // 1. Convert UDM to JSON string
    val jsonString = JsonRenderer.render(jsonData)

    // 2. Configure schema generator
    val config = SchemaGeneratorConfigBuilder(schemaVersion)
        .with(Option.DEFINITIONS_FOR_ALL_OBJECTS)
        .with(Option.NULLABLE_FIELDS_BY_DEFAULT)
        .with(Option.ADDITIONAL_PROPERTIES_BY_DEFAULT)
        .build()

    // 3. Generate schema
    val generator = SchemaGenerator(config)
    val objectMapper = ObjectMapper()
    val jsonNode = objectMapper.readTree(jsonString)
    val schemaNode = generator.generateSchema(jsonNode::class.java)

    // 4. Parse back to UDM
    return JsonSchemaParser.parse(schemaNode.toString())
}

// Multi-instance inference (better quality)
fun inferJsonSchemaFromMultiple(instances: List<UDM>): UDM {
    // Library supports merging multiple instances
    // for better type inference and required field detection
}
```

**Inference Quality Example**:

```json
// Input data:
{
  "name": "John",
  "age": 30,
  "email": "john@example.com"
}

// Inferred JSON Schema:
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "name": { "type": "string" },
    "age": { "type": "integer" },
    "email": {
      "type": "string",
      "format": "email"  // Smart pattern detection!
    }
  },
  "required": ["name", "age", "email"]
}
```

#### Option 2: Custom Implementation

**Overview**: Build on existing JSCH parser implementation.

| Aspect | Details |
|--------|---------|
| **Pros** | ✅ UDM-native, no conversion overhead<br>✅ No additional dependencies<br>✅ Simple for basic cases<br>✅ Full control |
| **Cons** | ❌ Complex for multi-instance merging<br>❌ Pattern detection (email, URI, etc.)<br>❌ Draft compatibility nuances<br>❌ Missing edge cases |
| **License** | N/A (own code) |
| **Quality** | 🟡 Good for simple cases, limited for complex |
| **Size Impact** | ✅ Zero |

**Example Implementation**:
```kotlin
fun inferJsonSchemaCustom(data: UDM): UDM.Object {
    return when (data) {
        is UDM.Scalar -> inferScalarType(data)
        is UDM.Array -> {
            val itemTypes = data.elements.map { inferJsonSchemaCustom(it) }.distinct()
            UDM.Object(mapOf(
                "type" to UDM.Scalar("array"),
                "items" to if (itemTypes.size == 1) itemTypes[0] else {
                    UDM.Object(mapOf("anyOf" to UDM.Array(itemTypes)))
                }
            ))
        }
        is UDM.Object -> {
            val properties = data.properties.mapValues { (_, v) ->
                inferJsonSchemaCustom(v)
            }
            UDM.Object(mapOf(
                "type" to UDM.Scalar("object"),
                "properties" to UDM.Object(properties),
                "required" to UDM.Array(data.properties.keys.map { UDM.Scalar(it) })
            ))
        }
        else -> UDM.Object(mapOf("type" to UDM.Scalar("null")))
    }
}

private fun inferScalarType(scalar: UDM.Scalar): UDM.Object {
    val value = scalar.value
    val type = when {
        value is Boolean -> "boolean"
        value is Number && value is Int -> "integer"
        value is Number -> "number"
        value is String && value.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.*")) -> {
            return UDM.Object(mapOf(
                "type" to UDM.Scalar("string"),
                "format" to UDM.Scalar("date-time")
            ))
        }
        value is String -> "string"
        else -> "string"
    }
    return UDM.Object(mapOf("type" to UDM.Scalar(type)))
}
```

### JSON Schema Inference Recommendation

**✅ Use External Library: `json-schema-inferrer`**

**Rationale**:

1. **High Value**: JSON Schema from JSON data is a **common use case**
2. **Quality**: Handles draft versions, optionality, type merging, pattern detection
3. **Manageable Size**: ~500KB total is acceptable for CLI fat JAR
4. **Maintained**: Active development with regular updates
5. **Time Savings**: Custom implementation would take weeks to match quality

**Size Impact Analysis**:
```
Current CLI fat JAR: ~15MB
With json-schema-inferrer: ~15.5MB (+3.3%)
Acceptable tradeoff for functionality
```

---

## Avro Schema Inference Options

### Apache Avro Built-in Capabilities

The Avro library (already a dependency) includes reflection-based schema generation:

```kotlin
import org.apache.avro.reflect.ReflectData

// From Java classes (built-in feature):
data class Person(val name: String, val age: Int)
val schema = ReflectData.get().getSchema(Person::class.java)

println(schema)
// Output:
// {"type":"record","name":"Person","fields":[
//   {"name":"name","type":"string"},
//   {"name":"age","type":"int"}
// ]}
```

### Options Analysis

#### Option 1: Use Avro ReflectData with Intermediate Classes

| Aspect | Details |
|--------|---------|
| **Pros** | ✅ Already a dependency (zero size increase)<br>✅ Standards-compliant<br>✅ Battle-tested by Apache |
| **Cons** | ❌ Requires UDM → Java Class → Schema<br>❌ Overhead of class generation<br>❌ Awkward API for data instances |
| **Size Impact** | ✅ Zero (already included) |

**Example**:
```kotlin
// Complex: UDM → Dynamic Class → Avro Schema
fun inferAvroViaReflection(data: UDM.Object): String {
    // 1. Generate dynamic Java class from UDM
    val dynamicClass = generateClassFromUdm(data) // Complex!

    // 2. Use Avro reflection
    val schema = ReflectData.get().getSchema(dynamicClass)

    return schema.toString(true)
}
```

#### Option 2: Custom UDM Analyzer

| Aspect | Details |
|--------|---------|
| **Pros** | ✅ Direct UDM analysis<br>✅ Simpler for data instances<br>✅ Full control over type mapping<br>✅ Avro schema is simpler than XSD |
| **Cons** | ❌ Custom implementation effort<br>❌ Need to handle Avro type nuances |
| **Size Impact** | ✅ Zero |
| **Quality** | 🟡 Good with careful implementation |

**Example Implementation**:
```kotlin
fun inferAvroSchema(
    data: UDM.Object,
    recordName: String = "InferredRecord",
    namespace: String? = null
): String {
    val fields = data.properties.map { (name, value) ->
        buildAvroField(name, value)
    }

    val schemaJson = buildJsonObject {
        put("type", "record")
        put("name", recordName)
        namespace?.let { put("namespace", it) }
        putJsonArray("fields") {
            fields.forEach { add(it) }
        }
    }

    return schemaJson.toString()
}

private fun buildAvroField(name: String, value: UDM): JsonObject {
    return buildJsonObject {
        put("name", name)
        put("type", inferAvroType(value))
    }
}

private fun inferAvroType(value: UDM): Any = when (value) {
    is UDM.Scalar -> when (value.value) {
        is Boolean -> "boolean"
        is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        is Double -> "double"
        is String -> "string"
        is ByteArray -> "bytes"
        null -> JsonArray().apply { add("null") }
        else -> "string"
    }
    is UDM.Array -> {
        val itemType = if (value.elements.isEmpty()) {
            "string" // Default
        } else {
            inferAvroType(value.elements.first())
        }
        buildJsonObject {
            put("type", "array")
            put("items", itemType)
        }
    }
    is UDM.Object -> {
        // Nested record
        buildJsonObject {
            put("type", "record")
            put("name", "NestedRecord")
            putJsonArray("fields") {
                value.properties.forEach { (name, nestedValue) ->
                    add(buildAvroField(name, nestedValue))
                }
            }
        }
    }
    else -> "string"
}
```

**Multi-Instance Inference** (Advanced):
```kotlin
fun inferAvroSchemaFromMultiple(instances: List<UDM.Object>): String {
    // Merge field sets from all instances
    val allFields = instances.flatMap { it.properties.keys }.distinct()

    // Determine if fields are nullable (missing in some instances)
    val fieldAnalysis = allFields.map { fieldName ->
        val presentIn = instances.count { it.properties.containsKey(fieldName) }
        val isOptional = presentIn < instances.size

        val values = instances.mapNotNull { it.properties[fieldName] }
        val inferredType = inferAvroType(values.first())

        fieldName to AvroFieldInfo(inferredType, isOptional)
    }

    // Build schema with union types for optional fields
    // ["null", "string"] for optional string field
}
```

### Avro Schema Inference Recommendation

**✅ Custom UDM Analyzer with Avro Library for Validation**

**Rationale**:

1. **Avro schemas are simpler** than XSD (more feasible to implement well)
2. **Direct UDM analysis** avoids intermediate class generation
3. **Leverage Avro library** for validation and serialization, not schema generation
4. **Zero size increase** (no new dependencies)

**Implementation Strategy**:
```kotlin
// Generate schema from UDM
val schema = AvroSchemaInference.infer(udmData)

// Validate schema using Avro library
val avroSchema = Schema.Parser().parse(schema)
val isValid = AvroValidator.validate(data, avroSchema)
```

---

## Architecture Proposal

### Recommended Analysis Module Structure

```
modules/analysis/
│
├── src/main/kotlin/com/glomidco/utlx/analysis/
│   │
│   ├── transformation/              # Schema format conversion
│   │   ├── SchemaTransformer.kt    # Main interface
│   │   ├── XsdToJsonSchema.kt      # ✅ Custom (already parsing XSD)
│   │   ├── JsonSchemaToAvro.kt     # ✅ Custom
│   │   ├── AvroToProtobuf.kt       # ✅ Custom
│   │   ├── XsdToAvro.kt            # ✅ Custom
│   │   └── ... (other combinations)
│   │
│   ├── inference/                   # Generate schemas from data
│   │   ├── SchemaInferrer.kt       # Main interface
│   │   ├── JsonSchemaInference.kt  # ✅ Uses json-schema-inferrer library
│   │   ├── AvroSchemaInference.kt  # ✅ Custom with Avro validation
│   │   └── XsdInference.kt         # ❌ Skip or minimal implementation
│   │
│   ├── validation/                  # Validate data against schemas
│   │   ├── SchemaValidator.kt      # Main interface
│   │   ├── XsdValidator.kt         # ✅ Use javax.xml.validation
│   │   ├── JsonSchemaValidator.kt  # ✅ Custom or use networknt/json-schema-validator
│   │   └── AvroValidator.kt        # ✅ Use Avro library
│   │
│   └── compatibility/               # Schema compatibility checking
│       ├── AvroCompatibility.kt    # Check schema evolution compatibility
│       └── JsonSchemaCompatibility.kt
│
└── build.gradle.kts
    dependencies {
        // Existing
        implementation(project(":modules:core"))
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

        // NEW: JSON Schema inference
        implementation("com.github.victools:jsonschema-generator:4.33.1")

        // Already have for formats
        // implementation("org.apache.avro:avro:1.11.3")  // via formats:avro
    }
```

### CLI Integration

```kotlin
// modules/cli/src/main/kotlin/com/glomidco/utlx/cli/commands/SchemaCommand.kt

object SchemaCommand {
    fun execute(args: Array<String>) {
        when (args.getOrNull(0)) {
            "transform" -> transformSchema(args.drop(1))
            "infer" -> inferSchema(args.drop(1))
            "validate" -> validateAgainstSchema(args.drop(1))
            "compatibility" -> checkCompatibility(args.drop(1))
            else -> printUsage()
        }
    }

    private fun inferSchema(args: List<String>) {
        // Parse: --from-data data.json --output-format json-schema
        val dataFile = parseDataFile(args)
        val outputFormat = parseOutputFormat(args) // json-schema, avro, xsd

        val data = parseInputData(dataFile)

        val schema = when (outputFormat) {
            "json-schema" -> JsonSchemaInference.infer(data)
            "avro" -> AvroSchemaInference.infer(data)
            "xsd" -> {
                println("XSD inference not supported. Use external tools like Trang.")
                println("Then use 'utlx schema transform' to convert to other formats.")
                return
            }
            else -> throw IllegalArgumentException("Unknown format: $outputFormat")
        }

        println(schema)
    }
}
```

### Example CLI Commands

```bash
# Infer JSON Schema from JSON data
utlx schema infer --from-data order.json --output-format json-schema -o order.schema.json

# Infer Avro schema from JSON data
utlx schema infer --from-data order.json --output-format avro -o order.avsc

# Transform existing schema formats
utlx schema transform --input order.xsd --output-format json-schema -o order.schema.json

# Validate data against schema
utlx schema validate --data order.json --schema order.schema.json

# Check schema compatibility (for evolution)
utlx schema compatibility --old v1.avsc --new v2.avsc
```

---

## Recommendations Summary

### Final Decision Matrix

| Schema Format | Inference Strategy | Library/Approach | Size Impact | Rationale |
|---------------|-------------------|------------------|-------------|-----------|
| **JSON Schema** | ✅ **Implement** | `json-schema-inferrer` v4.33.1 | +500KB | High value use case, quality implementation, active maintenance |
| **Avro Schema** | ✅ **Implement** | Custom UDM analyzer + Avro validation | 0KB | Simpler than XSD, already have Avro lib, direct UDM integration |
| **XSD** | ❌ **Skip** | N/A (recommend external tools) | 0KB | Low ROI, complexity, heavy dependencies, unreliable results |
| **Protobuf** | ⚠️ **Maybe** | Custom only (if demand exists) | 0KB | No good libraries, less common need |

### Dependency Additions

Add to `modules/analysis/build.gradle.kts`:

```kotlin
dependencies {
    // Existing dependencies
    implementation(project(":modules:core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("javax.xml.parsers:jaxp-api:1.4.2")
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // NEW: JSON Schema inference (RECOMMENDED)
    implementation("com.github.victools:jsonschema-generator:4.33.1")

    // OPTIONAL: JSON Schema validation (if validation feature added)
    // implementation("com.networknt:json-schema-validator:1.0.87")
}
```

### Size Impact Analysis

```
Current module sizes:
- modules/cli (fat JAR): ~15MB
- modules/analysis: ~50KB

With json-schema-inferrer:
- modules/cli (fat JAR): ~15.5MB (+3.3%)
- modules/analysis: ~550KB (+1100%)

Verdict: Acceptable for CLI distribution
```

---

## Implementation Priority

### Phase 1: JSON Schema Inference (High Priority)

**Timeline**: 1-2 weeks
**Value**: High - most requested feature

**Tasks**:
1. Add `json-schema-inferrer` dependency to `modules/analysis`
2. Implement `JsonSchemaInference.kt`:
   - Single instance inference
   - Multi-instance inference (better quality)
   - Configuration options (draft version, nullability strategy)
3. Add CLI command: `utlx schema infer`
4. Write tests with various JSON structures
5. Update documentation

**Deliverable**:
```bash
# Working command
utlx schema infer --from-data examples/order.json --output-format json-schema
```

### Phase 2: Avro Schema Inference (Medium Priority)

**Timeline**: 1 week
**Value**: Medium - useful for data engineering workflows

**Tasks**:
1. Implement `AvroSchemaInference.kt`:
   - Basic type mapping (UDM → Avro types)
   - Nested records
   - Array handling
   - Union types for nullable fields
2. Add multi-instance merging for better field optionality detection
3. Integration with existing Avro library for validation
4. Write tests
5. Update CLI and documentation

**Deliverable**:
```bash
# Working command
utlx schema infer --from-data data.json --output-format avro -o schema.avsc
```

### Phase 3: Schema Validation (Medium Priority)

**Timeline**: 1 week
**Value**: Medium - completes the schema toolchain

**Tasks**:
1. Implement `SchemaValidator.kt` interface
2. Add validators:
   - JSON Schema validator (consider `json-schema-validator` library)
   - Avro validator (use Avro library)
   - XSD validator (use javax.xml.validation)
3. CLI integration
4. Error reporting

**Deliverable**:
```bash
# Working command
utlx schema validate --data order.json --schema order.schema.json
```

### Phase 4: Enhanced Schema Transformation (Lower Priority)

**Timeline**: 2-3 weeks
**Value**: Medium - improves existing capability

**Tasks**:
1. Review existing transformation implementations
2. Add missing transformations:
   - JSON Schema → Avro (if not exists)
   - Avro → JSON Schema (if not exists)
   - Avro → Protobuf
3. Handle edge cases and complex types
4. Add transformation tests

### Future Considerations (Low Priority)

- **XSD Inference**: Only if strong user demand emerges
  - Implement basic version (element names + simple types only)
  - Document limitations clearly

- **Protobuf Schema Inference**: If gRPC adoption increases
  - Custom implementation only (no libraries available)
  - Focus on proto3 (simpler than proto2)

- **Schema Compatibility Checking**: For schema evolution
  - Avro has built-in compatibility checking
  - Implement for JSON Schema (breaking vs. non-breaking changes)

---

## Appendix: Alternative Libraries Considered

### For JSON Schema Inference

| Library | Status | Reason for Exclusion |
|---------|--------|---------------------|
| `jsonschema-generator` (saasquatch) | ❌ | Annotation-focused, not data-focused |
| `everit-org/json-schema` | ❌ | Validation only, no inference |
| Custom implementation | ⚠️ | Feasible but lower quality than victools |

### For XSD Inference

| Library | Status | Reason for Exclusion |
|---------|--------|---------------------|
| Sun JAXB | ❌ | Java class binding, not data inference |
| Trang | ⚠️ | Excellent but requires external process |
| XMLSpy API | ❌ | Commercial, expensive |
| Custom implementation | ⚠️ | Very complex for quality results |

### For Avro Schema Inference

| Library | Status | Reason for Exclusion |
|---------|--------|---------------------|
| Avro ReflectData | ⚠️ | Requires Java classes, awkward for UDM |
| Kite SDK | ❌ | Hadoop-focused, heavy dependencies |
| Custom implementation | ✅ | Best fit for UDM integration |

---

## Conclusion

The analysis module should focus on:

1. ✅ **JSON Schema inference** using `json-schema-inferrer` - highest value, quality implementation
2. ✅ **Avro schema inference** with custom UDM analyzer - simpler than XSD, zero dependencies
3. ❌ **Skip XSD inference** - low ROI, complexity, recommend external tools
4. ✅ **Prioritize schema transformation** - convert between existing schemas (already partially implemented)
5. ✅ **Add schema validation** - complete the toolchain

This approach balances:
- **Practicality**: Use proven libraries where they add value
- **Control**: Custom implementation for UDM integration
- **Size**: Minimal dependency bloat
- **Quality**: Professional results for end users

**Next Steps**: Proceed with Phase 1 implementation of JSON Schema inference using `json-schema-inferrer` library.

---

**Document Version**: 1.0
**Last Updated**: 2025-10-30
**Review Date**: 2025-12-01

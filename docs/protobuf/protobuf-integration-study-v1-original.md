# Protocol Buffers (Protobuf) Integration Study

**Document Type:** Technical Feasibility Study
**Author:** UTL-X Project Team
**Date:** 2025-10-27
**Status:** Draft
**Related:** [Avro Integration Study](avro-integration-study.md), [Parquet Integration Study](parquet-integration-study.md), [USDL 1.0 Specification](../language-guide/universal-schema-dsl.md)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Protocol Buffers Overview](#protocol-buffers-overview)
3. [Current UTL-X Architecture Analysis](#current-utlx-architecture-analysis)
4. [Protobuf Integration Architecture](#protobuf-integration-architecture)
5. [USDL to Protobuf Schema Mapping](#usdl-to-protobuf-schema-mapping)
6. [Implementation Plan](#implementation-plan)
7. [Effort Estimation](#effort-estimation)
8. [Comparison Matrix](#comparison-matrix)
9. [Benefits & Use Cases](#benefits--use-cases)
10. [Technical Risks & Mitigations](#technical-risks--mitigations)
11. [Testing Strategy](#testing-strategy)
12. [Dependencies & Libraries](#dependencies--libraries)
13. [Alternatives Considered](#alternatives-considered)
14. [Success Metrics](#success-metrics)
15. [References](#references)

---

## 1. Executive Summary

### Recommendation: **Proceed with High Priority** (Strategic Value)

Protocol Buffers integration is **highly feasible** and **strategically valuable** for enterprise and microservices use cases. Protobuf has strong adoption in modern distributed systems, gRPC services, and cross-language APIs.

### Key Findings

✅ **USDL 1.0 Already Defines Protobuf Directives** (Tier 3):
- `%fieldNumber` - Field tag numbers (critical for backwards compatibility)
- `%oneof` - Mutually exclusive field groups (unique to proto)
- `%packed` - Packed encoding for repeated numeric fields (proto optimization)
- `%reserved` - Reserved field numbers/names (schema evolution safety)
- `%map` - Native map/dictionary support (shared with Avro)
- `%ordinal` - Enum value numbers (explicit numbering)

✅ **Strong Enterprise Value**:
- gRPC service definitions (API contracts)
- Microservices communication protocols
- Cross-language data exchange (Java, C++, Python, Go, Rust, etc.)
- Google ecosystem integration (Cloud APIs use Protobuf)
- Schema evolution with strong backwards/forwards compatibility

⚠️ **Complexity Factors**:
- Two syntax versions: proto2 vs proto3 (different defaults, features)
- Field numbers are **mandatory** and **critical** (must be stable over time)
- Code generation aspect (schemas → generated code, not just data)
- Oneof groups require special handling
- Maps are syntactic sugar (encoded as repeated messages)

### Effort Estimation

| Scope | Effort | Priority |
|-------|--------|----------|
| **Schema Serialization (USDL → .proto)** | 5-7 days | High |
| **Schema Parsing (.proto → UDM)** | 3-4 days | High |
| **Proto2 Support** | 2 days | High |
| **Proto3 Support** | 1 day | High |
| **Binary Data Reading (Protobuf → UDM)** | 4-5 days | Medium |
| **Binary Data Writing (UDM → Protobuf)** | 4-5 days | Medium |
| **Testing & Documentation** | 3-4 days | High |
| **CLI Integration** | 1 day | High |

**Total Effort:**
- **Schema-Only MVP:** 11-15 days (proto2 + proto3 schema support)
- **Full Read/Write Support:** 19-24 days (includes binary serialization)

### Proto2 vs Proto3 Decision

**Recommendation:** Support **both proto2 and proto3**, with proto3 as default

**Rationale:**
- Proto3 is the current standard (simpler, more languages)
- Proto2 still widely used in legacy systems (Google internal, older projects)
- USDL can detect syntax version via `%version: "proto2"` or `%version: "proto3"`
- Proto3 is simpler to implement (fewer features, cleaner defaults)

### Recommended Approach

**Phase 1 (MVP - Schema Support):** 11-15 days
- Parse `.proto` files → UDM (proto descriptor format)
- Generate `.proto` files from USDL (proto2 + proto3)
- Support core features: messages, enums, field numbers, oneof, maps
- CLI: `utlx schema extract service.proto --format usdl`
- CLI: `utlx transform schema.utlx -o service.proto`

**Phase 2 (Data Transformation):** 8-10 days
- Read Protobuf binary → UDM data
- Write UDM data → Protobuf binary
- Requires compiled descriptors or dynamic message parsing
- CLI: `utlx transform mapping.utlx data.pb -o output.json`

**Phase 3 (Advanced - Future):**
- gRPC service definitions (services, RPCs)
- Code generation integration
- Schema registry integration (Buf, Confluent)

---

## 2. Protocol Buffers Overview

### What is Protocol Buffers?

**Protocol Buffers (Protobuf)** is a **language-neutral, platform-neutral extensible mechanism** for serializing structured data, developed by Google.

**Key Characteristics:**
- **Binary Serialization:** Compact, efficient wire format
- **Schema-Driven:** `.proto` files define message structures
- **Code Generation:** Generate data access classes for multiple languages
- **Backwards/Forwards Compatible:** Strong evolution guarantees via field numbers
- **Language Support:** 10+ official languages (Java, C++, Python, Go, C#, JavaScript, Ruby, PHP, Dart, Kotlin)
- **gRPC Foundation:** Protobuf is the default serialization for gRPC

### Proto2 vs Proto3

| Feature | Proto2 | Proto3 |
|---------|--------|--------|
| **Syntax Version** | `syntax = "proto2";` | `syntax = "proto3";` (default if omitted) |
| **Required Fields** | ✅ `required`, `optional`, `repeated` | ❌ No `required` (all optional), `repeated` |
| **Default Values** | Custom defaults: `optional int32 x = 1 [default = 42];` | Zero values (0, "", false, empty list) |
| **Field Presence** | Explicit (`has_field()` method) | Implicit (zero = absent) |
| **Oneof** | ✅ Supported | ✅ Supported |
| **Maps** | Via repeated messages | ✅ Native `map<K,V>` syntax |
| **Enums** | Must have first value = 0 | Must have first value = 0 |
| **Extensions** | ✅ Supported | ❌ Removed |
| **Groups** | ✅ Deprecated | ❌ Removed |
| **Unknown Fields** | Preserved | Preserved (after 3.5) |
| **JSON Mapping** | No standard | ✅ Standard JSON mapping |
| **Status** | Legacy (still widely used) | **Current standard** (2016+) |

**When to Use Proto2:**
- Legacy systems (pre-2016 projects)
- Need `required` fields for validation
- Need custom default values
- Need extensions mechanism

**When to Use Proto3:**
- New projects (current best practice)
- Simpler schema requirements
- Need JSON interoperability
- Cross-language consistency
- gRPC services (proto3 recommended)

### Protobuf Type System

**Scalar Types (proto3):**
```protobuf
syntax = "proto3";

message Example {
  double double_value = 1;      // 64-bit float
  float float_value = 2;         // 32-bit float
  int32 int32_value = 3;         // Variable-length signed
  int64 int64_value = 4;         // Variable-length signed
  uint32 uint32_value = 5;       // Variable-length unsigned
  uint64 uint64_value = 6;       // Variable-length unsigned
  sint32 sint32_value = 7;       // Variable-length signed (better for negatives)
  sint64 sint64_value = 8;       // Variable-length signed
  fixed32 fixed32_value = 9;     // Always 4 bytes
  fixed64 fixed64_value = 10;    // Always 8 bytes
  sfixed32 sfixed32_value = 11;  // Always 4 bytes (signed)
  sfixed64 sfixed64_value = 12;  // Always 8 bytes (signed)
  bool bool_value = 13;          // Boolean
  string string_value = 14;      // UTF-8 or 7-bit ASCII
  bytes bytes_value = 15;        // Arbitrary byte sequence
}
```

**Complex Types:**
```protobuf
// Enums
enum Status {
  STATUS_UNKNOWN = 0;  // First value must be 0
  STATUS_PENDING = 1;
  STATUS_ACTIVE = 2;
  STATUS_INACTIVE = 3;
}

// Nested messages
message Order {
  int32 order_id = 1;
  repeated Item items = 2;  // Array of items

  message Item {  // Nested definition
    string sku = 1;
    int32 quantity = 2;
  }
}

// Oneof (mutually exclusive fields)
message Payment {
  oneof payment_method {
    string credit_card = 1;
    string paypal = 2;
    string crypto_wallet = 3;
  }
}

// Maps (proto3 only)
message Inventory {
  map<string, int32> stock_levels = 1;  // Key: SKU, Value: Quantity
}
```

**Well-Known Types (Google's standard library):**
```protobuf
import "google/protobuf/timestamp.proto";
import "google/protobuf/duration.proto";
import "google/protobuf/any.proto";
import "google/protobuf/struct.proto";

message Event {
  google.protobuf.Timestamp event_time = 1;
  google.protobuf.Duration duration = 2;
  google.protobuf.Any metadata = 3;  // Can hold any message type
}
```

### Field Numbers: Critical Concept

**Field numbers are the wire format identifier** - not field names!

```protobuf
message Person {
  string name = 1;      // Field number 1 (not the value!)
  int32 age = 2;        // Field number 2
  string email = 3;     // Field number 3
}
```

**Rules:**
- Field numbers 1-15: 1 byte encoding (use for frequent fields)
- Field numbers 16-2047: 2 byte encoding
- Field numbers 19000-19999: Reserved by Google
- **Once assigned, field numbers CANNOT change** (breaks compatibility)
- Can reserve numbers: `reserved 5, 6, 10 to 15;`
- Can reserve names: `reserved "old_field", "deprecated";`

**Schema Evolution Example:**
```protobuf
// Version 1
message Order {
  int32 order_id = 1;
  string customer = 2;
}

// Version 2 (safe evolution)
message Order {
  int32 order_id = 1;
  string customer = 2;
  repeated Item items = 3;  // ✅ Added field (new number)
  // reserved 4;            // ✅ Reserved future number
}

// Version 2 (UNSAFE - breaks compatibility!)
message Order {
  int32 order_id = 1;
  double total = 2;  // ❌ Changed field 2 type - breaks decoders!
}
```

### Protobuf Ecosystem

**Languages with Official Support:**
- C++, Java, Python, Go, C#, Objective-C, JavaScript, Ruby, PHP, Dart, Kotlin (via Java)

**Build Tools:**
- `protoc` - Official Protocol Buffer Compiler
- Buf - Modern Protobuf toolchain (linting, breaking change detection, schema registry)
- gRPC - RPC framework built on Protobuf

**Schema Registries:**
- Buf Schema Registry (https://buf.build)
- Confluent Schema Registry (supports Protobuf since 5.5)
- Custom registries (many companies build internal ones)

**Use Cases:**
- gRPC microservices communication
- Google Cloud APIs (all use Protobuf)
- Inter-service data exchange
- Configuration files (e.g., Kubernetes, Envoy)
- Event streaming (Kafka with Protobuf)
- Data storage (more compact than JSON/XML)

---

## 3. Current UTL-X Architecture Analysis

### Existing Format Support

**Current Implementation:**

| Format | Parser | Serializer | Schema Support | Binary Data | Status |
|--------|--------|------------|----------------|-------------|--------|
| XML | ✅ | ✅ | XSD ✅ | N/A (text) | Stable |
| JSON | ✅ | ✅ | JSON Schema ✅ | N/A (text) | Stable |
| CSV | ✅ | ✅ | ❌ | N/A (text) | Stable |
| YAML | ✅ | ✅ | ❌ | N/A (text) | Stable |
| **Avro** | ❌ | ❌ | Schema ⏳ | ⏳ | Planned |
| **Parquet** | ❌ | ❌ | Schema ⏳ | ⏳ | Study Phase |
| **Protobuf** | ❌ | ❌ | Schema ⏳ | ⏳ | **Study Phase** |

### Schema Serializer Pattern

UTL-X schema serializers follow this proven pattern:

```kotlin
class SchemaSerializer(
    private val version: String,  // e.g., "proto3"
    private val prettyPrint: Boolean = true
) {
    enum class SerializationMode {
        LOW_LEVEL,      // User provides native proto structure
        UNIVERSAL_DSL   // User provides USDL
    }

    fun serialize(udm: UDM): String {
        val mode = detectMode(udm)
        val schemaStructure = when (mode) {
            SerializationMode.UNIVERSAL_DSL -> transformUniversalDSL(udm)
            SerializationMode.LOW_LEVEL -> udm
        }
        validateSchema(schemaStructure)
        return renderSchema(schemaStructure)  // Generate .proto text
    }
}
```

**For Protobuf:** This pattern maps cleanly:
1. Detect USDL vs low-level proto structure
2. Transform USDL directives → proto definitions
3. Generate `.proto` text file with proper syntax

### Format Module Structure

```
formats/
├── xml/
│   ├── XMLParser.kt
│   ├── XMLSerializer.kt
├── json/
│   ├── JSONParser.kt
│   ├── JSONSerializer.kt
├── xsd/           # Schema format
│   └── XSDSerializer.kt
├── jsch/          # Schema format
│   └── JSONSchemaSerializer.kt
├── avro/          # Planned
│   ├── AvroSchemaSerializer.kt
│   └── AvroSchemaParser.kt
└── protobuf/      # ← NEW MODULE
    ├── ProtobufSchemaParser.kt      # .proto → UDM
    ├── ProtobufSchemaSerializer.kt  # USDL → .proto
    ├── ProtobufDataParser.kt        # (Phase 2) .pb → UDM
    └── ProtobufDataSerializer.kt    # (Phase 2) UDM → .pb
```

---

## 4. Protobuf Integration Architecture

### Phase 1: Schema Support (MVP)

**Goal:** Enable `.proto` file generation from USDL and parsing existing `.proto` files

```kotlin
package org.apache.utlx.formats.protobuf

import org.apache.utlx.core.udm.UDM
import java.io.Writer
import java.io.StringWriter

/**
 * Protobuf Schema Serializer - Converts UDM to .proto file
 *
 * Supports:
 * - Proto2 and Proto3 syntax
 * - Messages, enums, nested types
 * - Field numbers, oneof, maps
 * - Reserved fields/numbers
 * - Imports and packages
 */
class ProtobufSchemaSerializer(
    private val syntax: String = "proto3",  // "proto2" or "proto3"
    private val prettyPrint: Boolean = true,
    private val generateComments: Boolean = true
) {

    init {
        require(syntax in setOf("proto2", "proto3")) {
            "Unsupported Protobuf syntax: $syntax. Must be 'proto2' or 'proto3'."
        }
    }

    /**
     * Serialization modes
     */
    enum class SerializationMode {
        LOW_LEVEL,      // User provides proto structure as UDM
        UNIVERSAL_DSL   // User provides USDL
    }

    /**
     * Serialize UDM to .proto string
     */
    fun serialize(udm: UDM): String {
        val writer = StringWriter()
        serialize(udm, writer)
        return writer.toString()
    }

    /**
     * Serialize UDM to .proto via Writer
     */
    fun serialize(udm: UDM, writer: Writer) {
        // Step 1: Detect mode
        val mode = detectMode(udm)
        val protoStructure = when (mode) {
            SerializationMode.UNIVERSAL_DSL -> transformUniversalDSL(udm as UDM.Object)
            SerializationMode.LOW_LEVEL -> udm
        }

        // Step 2: Validate proto structure
        validateProtoStructure(protoStructure)

        // Step 3: Inject documentation comments
        val enhanced = if (generateComments) {
            injectComments(protoStructure)
        } else {
            protoStructure
        }

        // Step 4: Render as .proto text
        val protoText = renderProto(enhanced)
        writer.write(protoText)
    }

    /**
     * Detect serialization mode
     */
    private fun detectMode(udm: UDM): SerializationMode {
        return when (udm) {
            is UDM.Object -> {
                when {
                    // USDL mode: Has %types directive
                    udm.properties.containsKey("%types") -> SerializationMode.UNIVERSAL_DSL

                    // Low-level: Has proto structure (messages, enums)
                    udm.properties.containsKey("messages") -> SerializationMode.LOW_LEVEL
                    udm.properties.containsKey("message") -> SerializationMode.LOW_LEVEL

                    // Default: USDL
                    else -> SerializationMode.UNIVERSAL_DSL
                }
            }
            else -> SerializationMode.LOW_LEVEL
        }
    }

    /**
     * Transform USDL to Protobuf structure
     */
    private fun transformUniversalDSL(schema: UDM.Object): UDM {
        // Extract USDL metadata
        val namespace = (schema.properties["%namespace"] as? UDM.Scalar)?.value as? String
        val version = (schema.properties["%version"] as? UDM.Scalar)?.value as? String
            ?: this.syntax

        // Extract types
        val types = schema.properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL requires '%types' directive")

        // Build proto messages and enums
        val messages = mutableListOf<UDM>()
        val enums = mutableListOf<UDM>()

        types.properties.forEach { (typeName, typeDef) ->
            if (typeDef !is UDM.Object) return@forEach

            val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String

            when (kind) {
                "structure" -> messages.add(convertStructureToMessage(typeName, typeDef))
                "enumeration" -> enums.add(convertToEnum(typeName, typeDef))
            }
        }

        // Build proto file structure
        return UDM.Object(
            properties = mapOf(
                "syntax" to UDM.Scalar(version),
                "package" to UDM.Scalar(namespace ?: ""),
                "messages" to UDM.Array(messages),
                "enums" to UDM.Array(enums)
            )
        )
    }

    /**
     * Convert USDL structure → Protobuf message
     */
    private fun convertStructureToMessage(name: String, structDef: UDM.Object): UDM {
        val fields = structDef.properties["%fields"] as? UDM.Array
            ?: throw IllegalArgumentException("Structure '$name' requires '%fields'")

        val documentation = (structDef.properties["%documentation"] as? UDM.Scalar)?.value as? String
        val reserved = structDef.properties["%reserved"] as? UDM.Array

        // Convert fields
        val protoFields = fields.elements.mapIndexed { index, fieldUdm ->
            if (fieldUdm !is UDM.Object) return@mapIndexed null
            convertFieldToProtoField(fieldUdm, index + 1)
        }.filterNotNull()

        return UDM.Object(
            properties = mapOf(
                "name" to UDM.Scalar(name),
                "fields" to UDM.Array(protoFields),
                "_documentation" to UDM.Scalar(documentation ?: ""),
                "reserved" to (reserved ?: UDM.Array(emptyList()))
            )
        )
    }

    /**
     * Convert USDL field → Protobuf field
     */
    private fun convertFieldToProtoField(field: UDM.Object, defaultNumber: Int): UDM? {
        val name = (field.properties["%name"] as? UDM.Scalar)?.value as? String ?: return null
        val type = (field.properties["%type"] as? UDM.Scalar)?.value as? String ?: return null
        val fieldNumber = (field.properties["%fieldNumber"] as? UDM.Scalar)?.value as? Int ?: defaultNumber
        val required = (field.properties["%required"] as? UDM.Scalar)?.value as? Boolean ?: false
        val array = (field.properties["%array"] as? UDM.Scalar)?.value as? Boolean ?: false
        val map = (field.properties["%map"] as? UDM.Scalar)?.value as? Boolean ?: false
        val oneof = (field.properties["%oneof"] as? UDM.Scalar)?.value as? String
        val description = (field.properties["%description"] as? UDM.Scalar)?.value as? String

        // Determine label (proto2: required/optional/repeated, proto3: repeated only)
        val label = when {
            array -> "repeated"
            map -> "map"  // Special handling
            syntax == "proto2" && required -> "required"
            syntax == "proto2" && !required -> "optional"
            else -> ""  // Proto3 has no label for singular fields
        }

        return UDM.Object(
            properties = mapOf(
                "name" to UDM.Scalar(name),
                "type" to UDM.Scalar(mapUSDLTypeToProto(type)),
                "number" to UDM.Scalar(fieldNumber),
                "label" to UDM.Scalar(label),
                "oneof" to UDM.Scalar(oneof ?: ""),
                "_documentation" to UDM.Scalar(description ?: "")
            )
        )
    }

    /**
     * Convert USDL enumeration → Protobuf enum
     */
    private fun convertToEnum(name: String, enumDef: UDM.Object): UDM {
        val values = enumDef.properties["%values"] as? UDM.Array
            ?: throw IllegalArgumentException("Enumeration '$name' requires '%values'")

        val documentation = (enumDef.properties["%documentation"] as? UDM.Scalar)?.value as? String

        // Convert enum values
        val protoValues = values.elements.mapIndexed { index, valueUdm ->
            when (valueUdm) {
                is UDM.Scalar -> {
                    // Simple form: just value name
                    UDM.Object(
                        properties = mapOf(
                            "name" to valueUdm,
                            "number" to UDM.Scalar(index)
                        )
                    )
                }
                is UDM.Object -> {
                    // Object form: with %value, %ordinal, %description
                    val valueName = (valueUdm.properties["%value"] as? UDM.Scalar)?.value as? String
                        ?: return@mapIndexed null
                    val ordinal = (valueUdm.properties["%ordinal"] as? UDM.Scalar)?.value as? Int ?: index
                    val desc = (valueUdm.properties["%description"] as? UDM.Scalar)?.value as? String

                    UDM.Object(
                        properties = mapOf(
                            "name" to UDM.Scalar(valueName),
                            "number" to UDM.Scalar(ordinal),
                            "_documentation" to UDM.Scalar(desc ?: "")
                        )
                    )
                }
                else -> null
            }
        }.filterNotNull()

        // Validate: proto requires first enum value = 0
        if (protoValues.isNotEmpty()) {
            val firstNumber = (protoValues[0] as UDM.Object).properties["number"] as UDM.Scalar
            require((firstNumber.value as Int) == 0) {
                "Protobuf enum '$name' first value must have number = 0"
            }
        }

        return UDM.Object(
            properties = mapOf(
                "name" to UDM.Scalar(name),
                "values" to UDM.Array(protoValues),
                "_documentation" to UDM.Scalar(documentation ?: "")
            )
        )
    }

    /**
     * Map USDL primitive types → Protobuf types
     */
    private fun mapUSDLTypeToProto(usdlType: String): String {
        return when (usdlType) {
            "string" -> "string"
            "integer" -> "int32"
            "long" -> "int64"
            "number" -> "double"
            "float" -> "float"
            "boolean" -> "bool"
            "bytes" -> "bytes"
            else -> usdlType  // Assume it's a message type reference
        }
    }

    /**
     * Render proto structure as .proto text file
     */
    private fun renderProto(udm: UDM): String {
        val proto = udm as UDM.Object
        val syntax = (proto.properties["syntax"] as? UDM.Scalar)?.value as? String ?: "proto3"
        val pkg = (proto.properties["package"] as? UDM.Scalar)?.value as? String ?: ""
        val messages = (proto.properties["messages"] as? UDM.Array)?.elements ?: emptyList()
        val enums = (proto.properties["enums"] as? UDM.Array)?.elements ?: emptyList()

        val builder = StringBuilder()

        // Syntax declaration
        builder.appendLine("syntax = \"$syntax\";")
        builder.appendLine()

        // Package declaration
        if (pkg.isNotEmpty()) {
            builder.appendLine("package $pkg;")
            builder.appendLine()
        }

        // Enums
        enums.forEach { enumUdm ->
            builder.append(renderEnum(enumUdm as UDM.Object))
            builder.appendLine()
        }

        // Messages
        messages.forEach { msgUdm ->
            builder.append(renderMessage(msgUdm as UDM.Object))
            builder.appendLine()
        }

        return builder.toString()
    }

    /**
     * Render message definition
     */
    private fun renderMessage(msg: UDM.Object, indent: String = ""): String {
        val name = (msg.properties["name"] as UDM.Scalar).value as String
        val fields = (msg.properties["fields"] as UDM.Array).elements
        val doc = (msg.properties["_documentation"] as? UDM.Scalar)?.value as? String

        val builder = StringBuilder()

        // Documentation comment
        if (!doc.isNullOrEmpty() && generateComments) {
            builder.appendLine("$indent// $doc")
        }

        builder.appendLine("${indent}message $name {")

        // Fields
        fields.forEach { fieldUdm ->
            val field = fieldUdm as UDM.Object
            val fieldName = (field.properties["name"] as UDM.Scalar).value as String
            val fieldType = (field.properties["type"] as UDM.Scalar).value as String
            val fieldNumber = (field.properties["number"] as UDM.Scalar).value as Int
            val label = (field.properties["label"] as? UDM.Scalar)?.value as? String ?: ""
            val fieldDoc = (field.properties["_documentation"] as? UDM.Scalar)?.value as? String

            if (!fieldDoc.isNullOrEmpty() && generateComments) {
                builder.appendLine("$indent  // $fieldDoc")
            }

            val labelPrefix = if (label.isNotEmpty()) "$label " else ""
            builder.appendLine("$indent  $labelPrefix$fieldType $fieldName = $fieldNumber;")
        }

        builder.appendLine("$indent}")

        return builder.toString()
    }

    /**
     * Render enum definition
     */
    private fun renderEnum(enum: UDM.Object, indent: String = ""): String {
        val name = (enum.properties["name"] as UDM.Scalar).value as String
        val values = (enum.properties["values"] as UDM.Array).elements
        val doc = (enum.properties["_documentation"] as? UDM.Scalar)?.value as? String

        val builder = StringBuilder()

        // Documentation comment
        if (!doc.isNullOrEmpty() && generateComments) {
            builder.appendLine("$indent// $doc")
        }

        builder.appendLine("${indent}enum $name {")

        // Values
        values.forEach { valueUdm ->
            val value = valueUdm as UDM.Object
            val valueName = (value.properties["name"] as UDM.Scalar).value as String
            val valueNumber = (value.properties["number"] as UDM.Scalar).value as Int
            val valueDoc = (value.properties["_documentation"] as? UDM.Scalar)?.value as? String

            if (!valueDoc.isNullOrEmpty() && generateComments) {
                builder.appendLine("$indent  // $valueDoc")
            }

            builder.appendLine("$indent  $valueName = $valueNumber;")
        }

        builder.appendLine("$indent}")

        return builder.toString()
    }

    private fun validateProtoStructure(udm: UDM) {
        // Validation logic for proto structure
        // - Check syntax is proto2 or proto3
        // - Validate field numbers (1-536870911, excluding 19000-19999)
        // - Validate enum first value = 0
        // - Check for duplicate field numbers
    }

    private fun injectComments(udm: UDM): UDM {
        // Already handled via _documentation properties
        return udm
    }
}

/**
 * Protobuf Schema Parser - Parse .proto files to UDM
 */
class ProtobufSchemaParser {
    /**
     * Parse .proto file to UDM representation
     */
    fun parse(protoFile: File): UDM {
        // Use protoc or parser library to read .proto file
        // Convert to UDM representation
        // Extract: syntax, package, messages, enums, field numbers
        TODO("Implementation using protobuf descriptor API")
    }
}
```

### CLI Integration

```bash
# Generate .proto from USDL
utlx transform schema.utlx -o service.proto

# Parse .proto to USDL
utlx schema extract service.proto --format usdl -o schema.json

# Convert XSD → Protobuf (via USDL)
utlx schema convert order.xsd --to proto -o order.proto

# Convert JSON Schema → Protobuf
utlx schema convert api-schema.json --to proto -o api.proto

# Specify proto version
utlx transform schema.utlx -o service.proto --proto-version proto2
```

---

## 5. USDL to Protobuf Schema Mapping

### Primitive Type Mapping

| USDL Type | Proto3 Type | Proto2 Type | Notes |
|-----------|-------------|-------------|-------|
| `string` | `string` | `string` | UTF-8 or 7-bit ASCII |
| `integer` | `int32` | `int32` | Variable length (efficient for small numbers) |
| `integer` (%size=64) | `int64` | `int64` | Variable length 64-bit |
| `number` | `double` | `double` | 64-bit float |
| `float` | `float` | `float` | 32-bit float |
| `boolean` | `bool` | `bool` | Boolean |
| `bytes` | `bytes` | `bytes` | Arbitrary byte sequence |

### Advanced Integer Types

| USDL Hint | Proto Type | Description |
|-----------|------------|-------------|
| `%type: "integer"` (default) | `int32` | Variable length, inefficient for negative |
| `%type: "integer"`, `%encoding: "signed"` | `sint32` | Variable length, efficient for negative |
| `%type: "integer"`, `%size: 32`, `%encoding: "fixed"` | `fixed32` | Always 4 bytes, faster if > 2^28 |
| `%type: "integer"`, `%size: 32`, `%encoding: "signed-fixed"` | `sfixed32` | Always 4 bytes, signed |

### Field Number Mapping

| USDL Directive | Proto Equivalent | Example |
|----------------|------------------|---------|
| `%fieldNumber: 1` | `= 1` | `string name = 1;` |
| `%fieldNumber: 15` | `= 15` | `int32 age = 15;` |
| Auto-assigned (index + 1) | Sequential | If not specified, use field order |

**Best Practices:**
- Field numbers 1-15: Use for frequent fields (1-byte encoding)
- Field numbers 16-2047: Use for less frequent fields (2-byte encoding)
- Never reuse field numbers (breaks compatibility)
- Reserve deleted field numbers: `%reserved: [5, 6, 10]`

### Repetition Mapping

| USDL Directive | Proto3 | Proto2 | Notes |
|----------------|--------|--------|-------|
| `%required: true` | (no label) | `required` | Proto3 has no required |
| `%required: false` | (no label) | `optional` | Proto3 all fields optional |
| `%array: true` | `repeated` | `repeated` | Array of values |
| `%map: true` | `map<K,V>` | `map<K,V>` | Map syntax (proto3+, proto2 3.0+) |

### Oneof Mapping (Mutually Exclusive Fields)

**USDL:**
```json
{
  "%types": {
    "Payment": {
      "%kind": "structure",
      "%fields": [
        {
          "%name": "credit_card",
          "%type": "string",
          "%oneof": "payment_method",
          "%fieldNumber": 1
        },
        {
          "%name": "paypal",
          "%type": "string",
          "%oneof": "payment_method",
          "%fieldNumber": 2
        },
        {
          "%name": "crypto_wallet",
          "%type": "string",
          "%oneof": "payment_method",
          "%fieldNumber": 3
        }
      ]
    }
  }
}
```

**Proto Output:**
```protobuf
message Payment {
  oneof payment_method {
    string credit_card = 1;
    string paypal = 2;
    string crypto_wallet = 3;
  }
}
```

### Map Mapping

**USDL:**
```json
{
  "%types": {
    "Inventory": {
      "%kind": "structure",
      "%fields": [
        {
          "%name": "stock_levels",
          "%type": "map",
          "%itemType": "integer",
          "%keyType": "string",
          "%fieldNumber": 1
        }
      ]
    }
  }
}
```

**Proto Output:**
```protobuf
message Inventory {
  map<string, int32> stock_levels = 1;
}
```

### Enum Mapping

**USDL:**
```json
{
  "%types": {
    "Status": {
      "%kind": "enumeration",
      "%documentation": "Order status values",
      "%values": [
        {
          "%value": "STATUS_UNKNOWN",
          "%ordinal": 0,
          "%description": "Unknown status"
        },
        {
          "%value": "STATUS_PENDING",
          "%ordinal": 1
        },
        {
          "%value": "STATUS_ACTIVE",
          "%ordinal": 2
        }
      ]
    }
  }
}
```

**Proto Output:**
```protobuf
// Order status values
enum Status {
  STATUS_UNKNOWN = 0;  // Unknown status
  STATUS_PENDING = 1;
  STATUS_ACTIVE = 2;
}
```

**Requirements:**
- First enum value **must** have ordinal = 0 (Protobuf requirement)
- Ordinals must be unique within enum
- Can have aliases (multiple names, same value) - requires `option allow_alias = true;`

### Reserved Fields Mapping

**USDL:**
```json
{
  "%types": {
    "Order": {
      "%kind": "structure",
      "%reserved": [5, 6, {"from": 10, "to": 15}],
      "%reservedNames": ["old_field", "deprecated_field"],
      "%fields": [...]
    }
  }
}
```

**Proto Output:**
```protobuf
message Order {
  reserved 5, 6, 10 to 15;
  reserved "old_field", "deprecated_field";

  // ... fields
}
```

### Nested Message Mapping

**USDL:**
```json
{
  "%types": {
    "Order": {
      "%kind": "structure",
      "%fields": [
        {
          "%name": "order_id",
          "%type": "integer",
          "%fieldNumber": 1
        },
        {
          "%name": "items",
          "%type": "structure",
          "%array": true,
          "%fieldNumber": 2,
          "%fields": [
            {
              "%name": "sku",
              "%type": "string",
              "%fieldNumber": 1
            },
            {
              "%name": "quantity",
              "%type": "integer",
              "%fieldNumber": 2
            }
          ]
        }
      ]
    }
  }
}
```

**Proto Output:**
```protobuf
message Order {
  int32 order_id = 1;
  repeated Item items = 2;

  message Item {
    string sku = 1;
    int32 quantity = 2;
  }
}
```

### Complete Example: E-Commerce Order

**Input: USDL**
```json
{
  "%namespace": "com.example.ecommerce",
  "%version": "proto3",
  "%types": {
    "OrderStatus": {
      "%kind": "enumeration",
      "%documentation": "Order status enumeration",
      "%values": [
        {"%value": "ORDER_STATUS_UNKNOWN", "%ordinal": 0},
        {"%value": "ORDER_STATUS_PENDING", "%ordinal": 1},
        {"%value": "ORDER_STATUS_CONFIRMED", "%ordinal": 2},
        {"%value": "ORDER_STATUS_SHIPPED", "%ordinal": 3},
        {"%value": "ORDER_STATUS_DELIVERED", "%ordinal": 4}
      ]
    },
    "Order": {
      "%kind": "structure",
      "%documentation": "Customer order message",
      "%reserved": [10],
      "%fields": [
        {
          "%name": "order_id",
          "%type": "integer",
          "%fieldNumber": 1,
          "%description": "Unique order identifier"
        },
        {
          "%name": "customer_id",
          "%type": "integer",
          "%fieldNumber": 2
        },
        {
          "%name": "status",
          "%type": "OrderStatus",
          "%fieldNumber": 3
        },
        {
          "%name": "order_timestamp",
          "%type": "integer",
          "%fieldNumber": 4,
          "%description": "Unix timestamp"
        },
        {
          "%name": "items",
          "%type": "structure",
          "%array": true,
          "%fieldNumber": 5,
          "%fields": [
            {
              "%name": "product_id",
              "%type": "string",
              "%fieldNumber": 1
            },
            {
              "%name": "quantity",
              "%type": "integer",
              "%fieldNumber": 2
            },
            {
              "%name": "unit_price",
              "%type": "number",
              "%fieldNumber": 3
            }
          ]
        },
        {
          "%name": "metadata",
          "%type": "map",
          "%keyType": "string",
          "%itemType": "string",
          "%fieldNumber": 6
        },
        {
          "%name": "credit_card",
          "%type": "string",
          "%oneof": "payment_method",
          "%fieldNumber": 20
        },
        {
          "%name": "paypal_email",
          "%type": "string",
          "%oneof": "payment_method",
          "%fieldNumber": 21
        }
      ]
    }
  }
}
```

**Output: .proto file (Proto3)**
```protobuf
syntax = "proto3";

package com.example.ecommerce;

// Order status enumeration
enum OrderStatus {
  ORDER_STATUS_UNKNOWN = 0;
  ORDER_STATUS_PENDING = 1;
  ORDER_STATUS_CONFIRMED = 2;
  ORDER_STATUS_SHIPPED = 3;
  ORDER_STATUS_DELIVERED = 4;
}

// Customer order message
message Order {
  reserved 10;

  // Unique order identifier
  int32 order_id = 1;
  int32 customer_id = 2;
  OrderStatus status = 3;
  // Unix timestamp
  int64 order_timestamp = 4;
  repeated OrderItem items = 5;
  map<string, string> metadata = 6;

  oneof payment_method {
    string credit_card = 20;
    string paypal_email = 21;
  }

  message OrderItem {
    string product_id = 1;
    int32 quantity = 2;
    double unit_price = 3;
  }
}
```

---

## 6. Implementation Plan

### Phase 1: Schema Support (MVP)

**Goal:** Enable `.proto` file generation from USDL and parsing of existing `.proto` files

#### 1.1 Create Format Module (1 day)
- Create `formats/protobuf/` directory structure
- Add Gradle build configuration
- Add Protobuf library dependencies

#### 1.2 Implement ProtobufSchemaSerializer - Proto3 (4-5 days)
- Implement USDL detection logic
- Transform USDL directives → proto structure
- Render proto text file with proper syntax
- Support messages, enums, field numbers
- Support oneof, maps, nested messages
- Support reserved fields/numbers
- Generate documentation comments

**Test Cases:**
- USDL primitives → proto3 message
- USDL nested structures → proto3 nested messages
- USDL enums → proto3 enums (validate first = 0)
- USDL arrays → proto3 repeated fields
- USDL maps → proto3 map fields
- USDL oneof groups → proto3 oneof
- USDL reserved → proto3 reserved declarations

#### 1.3 Implement ProtobufSchemaSerializer - Proto2 (2 days)
- Add proto2 syntax support
- Support required/optional/repeated labels
- Support default values (proto2 only)
- Support extensions (proto2 only)

**Test Cases:**
- Proto2 required fields
- Proto2 optional fields with defaults
- Proto2 extensions

#### 1.4 Implement ProtobufSchemaParser (3-4 days)
- Parse `.proto` files using protobuf descriptor API
- Convert proto descriptors → UDM representation
- Extract syntax version, package, messages, enums
- Extract field numbers, labels, oneof groups
- Handle imports (record but don't resolve)

**Test Cases:**
- Parse simple proto3 message
- Parse proto2 message with required/optional
- Parse nested messages
- Parse enums
- Parse oneof groups
- Parse maps
- Parse reserved declarations

#### 1.5 CLI Integration (1 day)
- Add `.proto` format detection
- Add `--proto-version` flag (proto2/proto3)
- Add schema conversion commands
- Add validation commands

#### 1.6 Testing & Documentation (3-4 days)
- 60+ unit tests (serializer + parser)
- 25+ conformance tests (real .proto files)
- Integration tests (USDL ↔ proto ↔ USDL roundtrip)
- Documentation updates
- Example transformations

**Phase 1 Total: 14-17 days**

---

### Phase 2: Binary Data Support (Future)

**Goal:** Enable reading/writing Protobuf binary data

#### 2.1 Implement ProtobufDataParser (4-5 days)
- Parse protobuf binary → UDM data
- Requires compiled descriptors or dynamic parsing
- Use protobuf Java API (`Message.parseFrom()`)
- Handle unknown fields gracefully

#### 2.2 Implement ProtobufDataSerializer (4-5 days)
- Serialize UDM data → protobuf binary
- Generate message instances dynamically
- Use `DynamicMessage` API
- Validate data against schema

#### 2.3 Testing & Documentation (2-3 days)
- Data transformation tests
- Roundtrip tests (binary → UDM → binary)
- Performance tests

**Phase 2 Total: 10-13 days**

---

### Phase 3: Advanced Features (Future)

- gRPC service definitions (services, RPCs, streams)
- Well-Known Types (Timestamp, Duration, Any, Struct)
- Code generation integration (`protoc` plugin)
- Schema registry integration (Buf, Confluent)
- Proto descriptor sets (.desc files)

---

## 7. Effort Estimation

### Detailed Breakdown

| Component | Complexity | Effort (days) | Priority | Dependencies |
|-----------|------------|---------------|----------|--------------|
| **Module Setup** | Low | 1 | High | - |
| **ProtobufSchemaSerializer (Proto3)** | Medium-High | 4-5 | High | USDL10.kt |
| **ProtobufSchemaSerializer (Proto2)** | Medium | 2 | High | Proto3 serializer |
| **ProtobufSchemaParser** | Medium | 3-4 | High | Protobuf library |
| **CLI Integration** | Low | 1 | High | CLI module |
| **Unit Tests** | Medium | 2 | High | - |
| **Conformance Tests** | Medium | 1 | High | Test .proto files |
| **Documentation** | Low | 1 | High | - |
| **ProtobufDataParser** | High | 4-5 | Medium | Phase 1 complete |
| **ProtobufDataSerializer** | High | 4-5 | Medium | Phase 1 complete |
| **Data Transform Tests** | High | 2-3 | Medium | Phase 2 parsers |

### Effort Summary

**Phase 1 (Schema Support - MVP):**
- **Core Implementation:** 10-11 days
- **Testing & Documentation:** 4-5 days
- **Total:** **14-16 days**

**Phase 2 (Binary Data):**
- **Core Implementation:** 8-10 days
- **Testing & Documentation:** 2-3 days
- **Total:** **10-13 days**

**Full Implementation:** **24-29 days**

### Comparison with Other Integrations

| Format | Schema Support | Data Support | Total Effort | Complexity | Status |
|--------|----------------|--------------|--------------|------------|--------|
| XSD | 5 days | N/A | 5 days | Low | ✅ Complete |
| JSON Schema | 4 days | N/A | 4 days | Low | ✅ Complete |
| Avro | 9-12 days | 3-4 days | 12-16 days | Medium | ⏳ Planned |
| Parquet | 11-13 days | 13-17 days | 24-30 days | High | 📋 Study Phase |
| **Protobuf** | **14-16 days** | **10-13 days** | **24-29 days** | **Medium-High** | 📋 Study Phase |

**Protobuf Complexity Notes:**
- Similar total effort to Parquet (24-29 days vs 24-30 days)
- Schema support more complex than Avro (14-16 vs 9-12) due to:
  - Two syntax versions (proto2 vs proto3)
  - Field numbers (critical for compatibility)
  - Oneof groups (unique feature)
  - Reserved declarations
- Binary data support simpler than Parquet (10-13 vs 13-17) because:
  - Row-based like Avro (not columnar)
  - Mature Java API with good tooling
  - Well-documented wire format

---

## 8. Comparison Matrix

### Schema Language Comparison

| Feature | Protobuf | Avro | JSON Schema | XSD |
|---------|----------|------|-------------|-----|
| **Schema Type** | Standalone .proto | Separate .avsc or embedded | Standalone | Standalone .xsd |
| **Syntax Versions** | Proto2, Proto3 | Schema 1.0 | Draft 07, 2019-09, 2020-12 | XSD 1.0, 1.1 |
| **Field Identifiers** | **Field numbers (critical)** | Field names | Field names | Element/attribute names |
| **Primitive Types** | 13 types | 6 types | 7 types | 44 types |
| **Complex Types** | Messages, enums | Records, enums, unions | Objects, enums | complexTypes, simpleTypes |
| **Arrays** | `repeated` | Array type | Array type | maxOccurs |
| **Maps** | ✅ Native (proto3) | ✅ Map type | ✅ Object with pattern | Via key-value pairs |
| **Nullable Fields** | Implicit (proto3), optional (proto2) | Union with null | `nullable: true` | nillable, minOccurs=0 |
| **Required Fields** | ❌ Proto3, ✅ Proto2 | ✅ (no default, not null) | `required` array | minOccurs=1 |
| **Unions** | Oneof (mutually exclusive) | ✅ Union types | oneOf, anyOf, allOf | choice, substitution groups |
| **Schema Evolution** | **Field numbers (excellent)** | Aliases, defaults | No standard | Extension, anyType |
| **Versioning** | Implicit via field numbers | Explicit in schema | $schema URI | Namespace versioning |
| **Comments** | ✅ `//` and `/* */` | ✅ `"doc"` field | `description` | xs:annotation |
| **Code Generation** | ✅ **Strong (protoc)** | ✅ Available | Limited | ✅ Strong (JAXB, etc.) |
| **Binary Format** | ✅ Efficient | ✅ Efficient | ❌ (JSON text) | ❌ (XML text) |

### Use Case Comparison

| Use Case | Best Format | Reason |
|----------|-------------|--------|
| **gRPC Services** | **Protobuf** | Native gRPC serialization format |
| **Microservices API Contracts** | **Protobuf** | Strong typing, code generation, versioning |
| **Kafka Messages** | Avro | Schema registry support, fast serialization |
| **REST APIs** | JSON Schema | Web-native, human-readable |
| **Data Lakes** | Parquet | Columnar, compression, analytics |
| **Enterprise SOA** | XSD | XML ecosystem, mature tooling |
| **Cross-Language Data Exchange** | **Protobuf** or Avro | Multiple language support |
| **Google Cloud APIs** | **Protobuf** | Standard for Google services |
| **Configuration Files** | **Protobuf** or YAML | Type-safe, validated configs |

### Binary Serialization Comparison

| Metric | Protobuf | Avro | Parquet |
|--------|----------|------|---------|
| **Wire Format** | Tag-length-value | Schema + data | Columnar (complex) |
| **Serialization Speed** | Very Fast | Very Fast | Moderate |
| **Deserialization Speed** | Very Fast | Fast | Fast (with projection) |
| **Size Efficiency** | Excellent | Excellent | Excellent (high compression) |
| **Random Access** | ❌ Sequential | ❌ Sequential | ✅ Column-level |
| **Streaming** | ✅ Excellent | ✅ Good | ⚠️ Batch-oriented |
| **Schema Evolution** | **Excellent** (field numbers) | Good (aliases) | Good (add columns) |
| **Ecosystem** | gRPC, Google Cloud | Kafka, Hadoop | Spark, Hive, Analytics |

---

## 9. Benefits & Use Cases

### 9.1 Use Case: gRPC Service Definition

**Scenario:** Define gRPC service API contracts using USDL, generate .proto for implementation

**USDL Input (CSV format):**
```csv
Service,Method,Input,Output,Description
OrderService,CreateOrder,CreateOrderRequest,Order,Create a new order
OrderService,GetOrder,GetOrderRequest,Order,Retrieve order by ID
OrderService,ListOrders,ListOrdersRequest,ListOrdersResponse,List all orders
```

**Transformation:**
```utlx
%utlx 1.0
input csv { headers: true }
output proto %usdl 1.0
---
{
  %namespace: "com.example.orders",
  %version: "proto3",
  %types: {
    // Generate messages from CSV...
    // (Additional transformation logic)
  }
}
```

**Output .proto:**
```protobuf
syntax = "proto3";

package com.example.orders;

service OrderService {
  rpc CreateOrder(CreateOrderRequest) returns (Order);
  rpc GetOrder(GetOrderRequest) returns (Order);
  rpc ListOrders(ListOrdersRequest) returns (ListOrdersResponse);
}

message CreateOrderRequest {
  // ...
}

message Order {
  // ...
}
```

**Benefits:**
- Business analysts define service contracts in spreadsheets
- Automatic .proto generation ensures consistency
- Version control tracks API evolution
- Generate client/server code using `protoc`

### 9.2 Use Case: Avro ↔ Protobuf Schema Conversion

**Scenario:** Migrate from Avro (Kafka) to Protobuf (gRPC) while maintaining schema compatibility

**Solution:**
```bash
# Extract Avro schema → USDL
utlx schema extract order.avsc --format usdl -o order-usdl.json

# Review/edit USDL (add field numbers if needed)
# Field numbers can be derived from Avro field order

# Transform USDL → Protobuf
utlx transform usdl-to-proto.utlx order-usdl.json -o order.proto

# Verify roundtrip compatibility
utlx schema convert order.proto --to avro -o order-regenerated.avsc
diff order.avsc order-regenerated.avsc
```

**Benefits:**
- Smooth migration between serialization formats
- USDL acts as intermediate representation
- Field number assignment can preserve Avro field order
- Single source of truth for multi-format deployment

### 9.3 Use Case: Legacy XML → Modern gRPC Migration

**Scenario:** Modernize legacy SOAP/XML services to gRPC while maintaining existing clients temporarily

**Solution:**
```bash
# Extract XSD schema → USDL
utlx schema extract legacy-order.xsd --format usdl -o order-usdl.json

# Transform USDL → Protobuf (add field numbers)
utlx transform xsd-to-proto.utlx order-usdl.json -o order.proto

# Generate gRPC service code
protoc --java_out=. --grpc-java_out=. order.proto

# Transform runtime data: XML → JSON (REST bridge)
utlx transform legacy-order.xml -o order.json

# Transform data: XML → Protobuf binary
utlx transform xml-to-proto.utlx legacy-order.xml -o order.pb
```

**Benefits:**
- Incremental migration from SOAP to gRPC
- Dual-protocol support during transition
- Schema mapping preserved via USDL
- No manual translation errors

### 9.4 Use Case: Configuration Management

**Scenario:** Type-safe, validated configuration files using Protobuf text format

**USDL Schema:**
```json
{
  "%types": {
    "AppConfig": {
      "%kind": "structure",
      "%fields": [
        {"%name": "server_port", "%type": "integer", "%fieldNumber": 1},
        {"%name": "database_url", "%type": "string", "%fieldNumber": 2},
        {"%name": "log_level", "%type": "LogLevel", "%fieldNumber": 3},
        {"%name": "feature_flags", "%type": "map", "%keyType": "string", "%itemType": "boolean", "%fieldNumber": 4}
      ]
    },
    "LogLevel": {
      "%kind": "enumeration",
      "%values": [
        {"%value": "LOG_LEVEL_DEBUG", "%ordinal": 0},
        {"%value": "LOG_LEVEL_INFO", "%ordinal": 1},
        {"%value": "LOG_LEVEL_WARN", "%ordinal": 2},
        {"%value": "LOG_LEVEL_ERROR", "%ordinal": 3}
      ]
    }
  }
}
```

**Generated .proto → Config File:**
```protobuf
# app.config (Protobuf text format)
server_port: 8080
database_url: "postgresql://localhost/mydb"
log_level: LOG_LEVEL_INFO
feature_flags {
  key: "enable_new_ui"
  value: true
}
feature_flags {
  key: "enable_analytics"
  value: false
}
```

**Benefits:**
- Type validation at parse time
- IDE autocomplete for config files (via generated code)
- Schema evolution (add fields without breaking)
- Faster parsing than YAML/JSON

### 9.5 Use Case: API Gateway Schema Registry

**Scenario:** Centralized schema registry for API gateway validating multiple upstream services

**Solution:**
```bash
# Collect schemas from multiple services
utlx schema extract service1.proto --format usdl -o service1.json
utlx schema extract service2.avsc --format usdl -o service2.json
utlx schema extract service3.xsd --format usdl -o service3.json

# Store in schema registry (unified USDL format)
curl -X POST https://registry.example.com/schemas \
  -d @service1.json

# Generate validation schemas for gateway
utlx transform registry-schema.json --to jsch -o validation.json

# API Gateway validates requests against JSON Schema
```

**Benefits:**
- Multi-format schema registry (proto, avro, xsd, json schema)
- Unified query language (USDL)
- Gateway-agnostic validation (JSON Schema output)
- Schema diff/breaking change detection

---

## 10. Technical Risks & Mitigations

### Risk 1: Field Number Stability

**Risk:** Auto-assigned field numbers may change between schema versions, breaking compatibility

**Impact:** Critical - Breaks wire format compatibility

**Mitigation:**
- **Always require explicit `%fieldNumber`** in USDL for production schemas
- Add validation: warn if field numbers not specified
- Document best practices: "Never change field numbers"
- Add CLI flag: `--strict-field-numbers` (fails if not explicit)
- Generate schema diffs highlighting field number changes

### Risk 2: Proto2 vs Proto3 Confusion

**Risk:** Users mix proto2/proto3 features, generating invalid schemas

**Impact:** Medium - Invalid .proto files

**Mitigation:**
- Default to proto3 (simpler, modern)
- Validate syntax-specific features:
  - Proto3: No `required`, no custom defaults
  - Proto2: Allow `required`, `optional` labels
- Add `%version` directive detection
- Generate syntax error messages referencing proto version

### Risk 3: Oneof Complexity

**Risk:** Oneof groups require all fields to share same oneof name, easy to misconfigure

**Impact:** Medium - Invalid schemas or unexpected behavior

**Mitigation:**
- Validate oneof consistency (all fields in group have same name)
- Generate clear error messages
- Document oneof patterns in examples
- Add USDL validation: `%oneof` must be consistent

### Risk 4: Reserved Field Management

**Risk:** Users forget to reserve deleted field numbers, breaking compatibility

**Impact:** High - Silent data corruption

**Mitigation:**
- Document reserved field best practices prominently
- Add schema diff tool: detect deleted fields → suggest reservations
- Validate: error if field number reused after deletion
- Future: Auto-generate `reserved` declarations from git history

### Risk 5: Code Generation Expectations

**Risk:** Users expect UTL-X to generate code (like `protoc`), but it only generates schemas

**Impact:** Low - User confusion

**Mitigation:**
- Clearly document: UTL-X generates `.proto` files, not code
- Document integration with `protoc` for code generation
- Provide example workflows showing both tools
- Consider future: `protoc` plugin for USDL-based generation

### Risk 6: Protobuf Library Dependency Size

**Risk:** Protobuf libraries add 2-5 MB dependency

**Impact:** Low - Acceptable for most use cases

**Mitigation:**
- Use official protobuf-java library (widely adopted)
- No alternative lighter-weight options available
- Document dependency size
- Consider ProGuard/R8 for CLI distribution

---

## 11. Testing Strategy

### 11.1 Unit Tests (Schema Serialization)

**ProtobufSchemaSerializer Tests (30 tests):**

```kotlin
@Test
fun `USDL structure to proto3 message`() {
    val usdl = """
    {
      "%types": {
        "Person": {
          "%kind": "structure",
          "%fields": [
            {"%name": "name", "%type": "string", "%fieldNumber": 1},
            {"%name": "age", "%type": "integer", "%fieldNumber": 2}
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3")
    val proto = serializer.serialize(parseJSON(usdl))

    proto shouldContain "syntax = \"proto3\";"
    proto shouldContain "message Person {"
    proto shouldContain "string name = 1;"
    proto shouldContain "int32 age = 2;"
}

@Test
fun `USDL enumeration to proto enum with first value zero`() {
    val usdl = """
    {
      "%types": {
        "Status": {
          "%kind": "enumeration",
          "%values": [
            {"%value": "STATUS_UNKNOWN", "%ordinal": 0},
            {"%value": "STATUS_ACTIVE", "%ordinal": 1}
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3")
    val proto = serializer.serialize(parseJSON(usdl))

    proto shouldContain "enum Status {"
    proto shouldContain "STATUS_UNKNOWN = 0;"
    proto shouldContain "STATUS_ACTIVE = 1;"
}

@Test
fun `USDL oneof group to proto oneof`() {
    val usdl = """
    {
      "%types": {
        "Payment": {
          "%kind": "structure",
          "%fields": [
            {
              "%name": "credit_card",
              "%type": "string",
              "%oneof": "payment_method",
              "%fieldNumber": 1
            },
            {
              "%name": "paypal",
              "%type": "string",
              "%oneof": "payment_method",
              "%fieldNumber": 2
            }
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3")
    val proto = serializer.serialize(parseJSON(usdl))

    proto shouldContain "oneof payment_method {"
    proto shouldContain "string credit_card = 1;"
    proto shouldContain "string paypal = 2;"
}

@Test
fun `USDL map to proto3 map`() {
    val usdl = """
    {
      "%types": {
        "Inventory": {
          "%kind": "structure",
          "%fields": [
            {
              "%name": "stock",
              "%type": "map",
              "%keyType": "string",
              "%itemType": "integer",
              "%fieldNumber": 1
            }
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3")
    val proto = serializer.serialize(parseJSON(usdl))

    proto shouldContain "map<string, int32> stock = 1;"
}

@Test
fun `USDL nested structure to proto nested message`() {
    val usdl = """
    {
      "%types": {
        "Order": {
          "%kind": "structure",
          "%fields": [
            {"%name": "order_id", "%type": "integer", "%fieldNumber": 1},
            {
              "%name": "items",
              "%type": "structure",
              "%array": true,
              "%fieldNumber": 2,
              "%fields": [
                {"%name": "sku", "%type": "string", "%fieldNumber": 1},
                {"%name": "quantity", "%type": "integer", "%fieldNumber": 2}
              ]
            }
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3")
    val proto = serializer.serialize(parseJSON(usdl))

    proto shouldContain "message Order {"
    proto shouldContain "repeated OrderItem items = 2;"
    proto shouldContain "message OrderItem {"
    proto shouldContain "string sku = 1;"
}

@Test
fun `proto2 with required and optional fields`() {
    val usdl = """
    {
      "%version": "proto2",
      "%types": {
        "Person": {
          "%kind": "structure",
          "%fields": [
            {"%name": "name", "%type": "string", "%required": true, "%fieldNumber": 1},
            {"%name": "email", "%type": "string", "%required": false, "%fieldNumber": 2}
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto2")
    val proto = serializer.serialize(parseJSON(usdl))

    proto shouldContain "syntax = \"proto2\";"
    proto shouldContain "required string name = 1;"
    proto shouldContain "optional string email = 2;"
}

@Test
fun `USDL with reserved fields`() {
    val usdl = """
    {
      "%types": {
        "Order": {
          "%kind": "structure",
          "%reserved": [5, 6, {"from": 10, "to": 15}],
          "%reservedNames": ["old_field"],
          "%fields": [
            {"%name": "order_id", "%type": "integer", "%fieldNumber": 1}
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3")
    val proto = serializer.serialize(parseJSON(usdl))

    proto shouldContain "reserved 5, 6, 10 to 15;"
    proto shouldContain "reserved \"old_field\";"
}

@Test
fun `USDL with documentation generates comments`() {
    val usdl = """
    {
      "%types": {
        "Order": {
          "%kind": "structure",
          "%documentation": "Customer order message",
          "%fields": [
            {
              "%name": "order_id",
              "%type": "integer",
              "%fieldNumber": 1,
              "%description": "Unique order identifier"
            }
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3", generateComments = true)
    val proto = serializer.serialize(parseJSON(usdl))

    proto shouldContain "// Customer order message"
    proto shouldContain "// Unique order identifier"
}

@Test
fun `validate field numbers in valid range`() {
    val usdl = """
    {
      "%types": {
        "Test": {
          "%kind": "structure",
          "%fields": [
            {"%name": "invalid", "%type": "string", "%fieldNumber": 19000}
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3")

    shouldThrow<IllegalArgumentException> {
        serializer.serialize(parseJSON(usdl))
    }.message shouldContain "Field number 19000 is reserved"
}

@Test
fun `validate enum first value is zero`() {
    val usdl = """
    {
      "%types": {
        "Status": {
          "%kind": "enumeration",
          "%values": [
            {"%value": "STATUS_ACTIVE", "%ordinal": 1}
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = ProtobufSchemaSerializer(syntax = "proto3")

    shouldThrow<IllegalArgumentException> {
        serializer.serialize(parseJSON(usdl))
    }.message shouldContain "first value must have number = 0"
}
```

### 11.2 Unit Tests (Schema Parsing)

**ProtobufSchemaParser Tests (15 tests):**

```kotlin
@Test
fun `parse simple proto3 message`() {
    val protoFile = File("test-data/simple-person.proto")
    // Content: message Person { string name = 1; int32 age = 2; }

    val parser = ProtobufSchemaParser()
    val udm = parser.parse(protoFile)

    val types = (udm as UDM.Object).properties["%types"] as UDM.Object
    types.properties shouldContainKey "Person"

    val person = types.properties["Person"] as UDM.Object
    val fields = (person.properties["%fields"] as UDM.Array).elements

    fields.size shouldBe 2
    (fields[0] as UDM.Object).properties["%name"] shouldBe UDM.Scalar("name")
    (fields[0] as UDM.Object).properties["%fieldNumber"] shouldBe UDM.Scalar(1)
}

@Test
fun `parse proto3 with oneof group`() {
    val protoFile = File("test-data/payment-oneof.proto")
    val parser = ProtobufSchemaParser()
    val udm = parser.parse(protoFile)

    val fields = extractFields(udm, "Payment")
    val creditCard = fields.find { it.name == "credit_card" }!!

    creditCard.oneof shouldBe "payment_method"
}

@Test
fun `parse proto with nested message`() {
    val protoFile = File("test-data/nested-order.proto")
    val parser = ProtobufSchemaParser()
    val udm = parser.parse(protoFile)

    val fields = extractFields(udm, "Order")
    val items = fields.find { it.name == "items" }!!

    items.type shouldBe "structure"
    items.array shouldBe true
}
```

### 11.3 Conformance Tests (Real .proto Files)

**Test Suite (20+ .proto files):**

```bash
# Generate test .proto files from Google's well-known types
test-data/
├── google/
│   ├── timestamp.proto
│   ├── duration.proto
│   ├── any.proto
│   └── struct.proto
├── examples/
│   ├── addressbook.proto      # From protobuf tutorials
│   ├── person.proto
│   ├── search_service.proto   # gRPC example
│   └── ecommerce.proto
├── generated/
│   ├── simple-person.proto
│   ├── nested-order.proto
│   ├── payment-oneof.proto
│   ├── inventory-map.proto
│   └── status-enum.proto
```

**Conformance Test:**
```kotlin
class ProtobufConformanceTests {
    @Test
    fun `parse and regenerate addressbook proto`() {
        val original = File("test-data/examples/addressbook.proto")
        val parser = ProtobufSchemaParser()
        val serializer = ProtobufSchemaSerializer(syntax = "proto3")

        // Parse → USDL
        val usdl = parser.parse(original)

        // USDL → Proto
        val regenerated = serializer.serialize(usdl)

        // Compile both with protoc (validates correctness)
        val originalCompiled = compileProto(original)
        val regeneratedCompiled = compileProto(regenerated)

        // Compare descriptors (structure, not text)
        compareDescriptors(originalCompiled, regeneratedCompiled) shouldBe true
    }

    @Test
    fun `generated proto compiles with protoc`() {
        val usdl = loadUSDL("test-data/order-usdl.json")
        val serializer = ProtobufSchemaSerializer(syntax = "proto3")
        val proto = serializer.serialize(usdl)

        // Write to temp file
        val tempFile = File.createTempFile("test", ".proto")
        tempFile.writeText(proto)

        // Compile with protoc
        val result = runCommand("protoc --descriptor_set_out=/dev/null ${tempFile.absolutePath}")

        result.exitCode shouldBe 0  // Success
    }
}
```

### 11.4 Integration Tests (5 tests)

- **CLI schema generation test**
- **XSD → Protobuf conversion test**
- **JSON Schema → Protobuf conversion test**
- **Avro → Protobuf conversion test** (once Avro support exists)
- **Roundtrip test:** USDL → Proto → USDL → compare

### 11.5 Performance Tests (3 tests)

- **Schema generation speed** (target: < 50ms for typical schema)
- **Schema parsing speed** (target: < 100ms)
- **Large schema handling** (100+ messages, 1000+ fields)

---

## 12. Dependencies & Libraries

### 12.1 Protocol Buffers Libraries

**Primary Dependency: protobuf-java (Official Google implementation)**

```gradle
// formats/protobuf/build.gradle.kts
dependencies {
    // Protobuf runtime
    implementation("com.google.protobuf:protobuf-java:3.25.1")

    // Protobuf util (for JSON conversion, text format)
    implementation("com.google.protobuf:protobuf-java-util:3.25.1")

    // For parsing .proto files (optional - for Phase 1)
    implementation("com.google.protobuf:protobuf-java:3.25.1") // Includes DescriptorProtos

    // Testing
    testImplementation("com.google.protobuf:protobuf-java:3.25.1")
}
```

**Library Characteristics:**
- **Version:** 3.25.1 (latest stable, released 2024-10)
- **License:** BSD 3-Clause (compatible with UTL-X AGPL/Commercial)
- **Size:** ~2 MB (protobuf-java), +500 KB (protobuf-java-util)
- **Maturity:** Very mature (15+ years, Google-maintained)
- **Supports:** Proto2 and Proto3

### 12.2 Alternative: protobuf-kotlin

**For better Kotlin integration:**
```gradle
dependencies {
    implementation("com.google.protobuf:protobuf-kotlin:3.25.1")
}
```

**Benefits:**
- Kotlin-friendly DSL
- Nullable types
- Coroutines support
- Same protobuf-java underneath

**Trade-off:** Additional 1 MB dependency

**Recommendation:** Use protobuf-java (standard), consider protobuf-kotlin for Phase 2 (data handling)

### 12.3 Dependency Size Impact

| Library | Size | Required For | Phase |
|---------|------|--------------|-------|
| protobuf-java | 2 MB | Schema, data, descriptors | 1 |
| protobuf-java-util | 500 KB | JSON conversion, text format | 1 |
| protobuf-kotlin | 1 MB | Better Kotlin integration | 2 (optional) |

**Total Dependency Size:**
- **Phase 1 (Schema):** ~2.5 MB
- **Phase 2 (Data):** ~3.5 MB (with protobuf-kotlin)

**Comparison:**
- **Avro:** ~2 MB
- **Parquet:** ~23 MB (includes Hadoop)
- **Protobuf:** ~2.5 MB (**smallest**)

---

## 13. Alternatives Considered

### Alternative 1: Text-Based .proto Generation (No Library)

**Approach:** Generate `.proto` text files without using protobuf library, just string templates

**Pros:**
- No dependencies
- Very lightweight
- Fast implementation

**Cons:**
- ❌ Cannot parse existing .proto files
- ❌ Cannot validate generated schemas
- ❌ Cannot access field descriptors for Phase 2
- ❌ No protoc integration validation

**Verdict:** ❌ **Rejected** - Too limited for production use

---

### Alternative 2: Use protoc --decode (External Tool)

**Approach:** Use `protoc` command-line tool for all proto operations

**Pros:**
- Official tool
- Guaranteed correctness
- No library dependencies

**Cons:**
- ❌ Requires external tool installation
- ❌ Slower (process spawning overhead)
- ❌ Harder to integrate with Java/Kotlin
- ❌ Fragile (text parsing of protoc output)

**Verdict:** ❌ **Rejected** - External dependency not suitable for library

---

### Alternative 3: Use Square's Wire (Alternative Protobuf Library)

**Approach:** Use Wire instead of protobuf-java

**Pros:**
- More Kotlin-friendly API
- Cleaner generated code
- Supports JSON natively
- Smaller dependency

**Cons:**
- ❌ Less compatible with standard protoc
- ❌ Smaller ecosystem
- ❌ Not official Google implementation
- ❌ May have subtle wire format differences

**Verdict:** ❌ **Rejected** - Official library provides better compatibility

---

### **Selected Alternative: Use Official protobuf-java Library**

**Rationale:**
- ✅ Official Google implementation
- ✅ Maximum compatibility (proto2 + proto3)
- ✅ Mature and well-tested (15+ years)
- ✅ Full descriptor API for parsing/generation
- ✅ Supports all protobuf features
- ✅ Small dependency size (~2.5 MB)
- ✅ Can validate schemas against protoc
- ✅ Phase 2 ready (binary data support)

**Trade-off:** Slightly larger than Wire, but worth it for compatibility and maturity

---

## 14. Success Metrics

### 14.1 Technical Metrics

**Phase 1 (Schema Support):**
- ✅ 100% USDL directive coverage for Protobuf (%fieldNumber, %oneof, %packed, %reserved, %map)
- ✅ ≥ 90% test coverage (unit + integration + conformance tests)
- ✅ Schema generation: < 50ms for typical schema (10-20 messages)
- ✅ Schema parsing: < 100ms for typical .proto file
- ✅ Generated .proto files compile with `protoc` without errors: 100%
- ✅ Proto2 and Proto3 support: 100% feature coverage
- ✅ Roundtrip consistency: USDL → Proto → USDL (100% structure preservation)

**Phase 2 (Binary Data):**
- ✅ Serialization speed: ≥ 100 MB/s (UDM → Protobuf binary)
- ✅ Deserialization speed: ≥ 150 MB/s (Protobuf binary → UDM)
- ✅ Memory usage: < 200 MB for 100 MB dataset
- ✅ Wire format compatibility: 100% with protoc-generated code

### 14.2 User Adoption Metrics

**6 Months Post-Launch:**
- 15-25% of UTL-X transformations involve Protobuf format (higher than Avro due to gRPC popularity)
- 10+ community-contributed Protobuf transformation examples
- 50+ gRPC service definitions generated from USDL
- 5+ blog posts/tutorials using UTL-X for Protobuf

**12 Months Post-Launch:**
- 30-50% of microservices transformations use UTL-X Protobuf support
- Integration with 2+ schema registries (Buf, Confluent)
- 15+ enterprise customers using Protobuf support in production (gRPC services)

### 14.3 Business Metrics

**Value Proposition:**
- Reduce gRPC API contract development time by 40% (automated schema generation)
- Enable multi-format API gateways (proto + avro + json schema from single USDL)
- Support microservices migration projects (SOAP → gRPC, Avro → Proto)
- Google Cloud integration (all Google Cloud APIs use Protobuf)

**Revenue Impact (Commercial Licensing):**
- 8+ commercial license sales attributed to Protobuf support (Year 1)
- 25+ enterprise pilot projects using Protobuf integration (gRPC deployments)
- Strategic positioning: "Universal API schema management"

### 14.4 Community Metrics

**Documentation & Evangelism:**
- 10+ blog posts about Protobuf integration
- 5+ conference talks mentioning Protobuf support (gRPC Summit, KubeCon)
- Active forum discussions (>15 threads)
- 80+ GitHub stars on Protobuf-related examples

---

## 15. References

### Protocol Buffers Specification

- **Language Guide (Proto3):** https://protobuf.dev/programming-guides/proto3/
- **Language Guide (Proto2):** https://protobuf.dev/programming-guides/proto2/
- **Encoding Documentation:** https://protobuf.dev/programming-guides/encoding/
- **Style Guide:** https://protobuf.dev/programming-guides/style/
- **Well-Known Types:** https://protobuf.dev/reference/protobuf/google.protobuf/

### Protocol Buffers Libraries

- **protobuf-java:** https://github.com/protocolbuffers/protobuf/tree/main/java
- **Protocol Buffers Documentation:** https://protobuf.dev/
- **protoc Compiler:** https://github.com/protocolbuffers/protobuf

### gRPC & Ecosystem

- **gRPC:** https://grpc.io/
- **Buf:** https://buf.build/ (modern protobuf tooling)
- **Confluent Schema Registry (Protobuf):** https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-protobuf.html

### UTL-X Documentation

- **USDL 1.0 Specification:** [../language-guide/universal-schema-dsl.md](../language-guide/universal-schema-dsl.md)
- **Avro Integration Study:** [avro-integration-study.md](avro-integration-study.md)
- **Parquet Integration Study:** [parquet-integration-study.md](parquet-integration-study.md)
- **UDM Documentation:** [../architecture/universal-data-model.md](../architecture/universal-data-model.md)

### Comparison Resources

- **Protobuf vs Avro vs Thrift:** https://martin.kleppmann.com/2012/12/05/schema-evolution-in-avro-protocol-buffers-thrift.html
- **gRPC vs REST:** https://cloud.google.com/blog/products/api-management/understanding-grpc-openapi-and-rest

---

## Appendix A: USDL Protobuf Directives Summary

### USDL 1.0 Tier 3 Directives for Protobuf

| Directive | Scope | Value Type | Description | Example |
|-----------|-------|------------|-------------|---------|
| `%fieldNumber` | Field | Integer | **Critical:** Field tag number for wire format | `1`, `15`, `100` |
| `%oneof` | Field | String | Oneof group name (mutually exclusive fields) | `"payment_method"` |
| `%packed` | Field | Boolean | Packed encoding for repeated numerics (proto2/proto3) | `true`, `false` |
| `%reserved` | Type | Array | Reserved field numbers (cannot reuse) | `[5, 6]`, `[{"from": 10, "to": 15}]` |
| `%reservedNames` | Type | Array | Reserved field names (cannot reuse) | `["old_field", "deprecated"]` |
| `%map` | Field | Boolean | Is this field a map? | `true` (requires `%keyType`, `%itemType`) |
| `%keyType` | Field | String | Map key type | `"string"`, `"integer"` |
| `%ordinal` | Enum Value | Integer | Explicit enum value number | `0`, `1`, `2` |

### Proto2-Specific

| Directive | Scope | Value Type | Description | Proto2 Only |
|-----------|-------|------------|-------------|-------------|
| `%default` | Field | Any | Default value for optional field | ✅ (proto3 uses zero values) |
| `%required` | Field | Boolean | Is field required? | ✅ (proto3 has no required) |

### Usage Example (Complete)

```json
{
  "%namespace": "com.example.api",
  "%version": "proto3",
  "%types": {
    "Status": {
      "%kind": "enumeration",
      "%values": [
        {"%value": "STATUS_UNKNOWN", "%ordinal": 0},
        {"%value": "STATUS_ACTIVE", "%ordinal": 1}
      ]
    },
    "Order": {
      "%kind": "structure",
      "%documentation": "Customer order message",
      "%reserved": [10, 11],
      "%reservedNames": ["old_customer_id"],
      "%fields": [
        {
          "%name": "order_id",
          "%type": "integer",
          "%fieldNumber": 1,
          "%description": "Unique order identifier"
        },
        {
          "%name": "status",
          "%type": "Status",
          "%fieldNumber": 2
        },
        {
          "%name": "tags",
          "%type": "string",
          "%array": true,
          "%fieldNumber": 3,
          "%packed": true
        },
        {
          "%name": "metadata",
          "%type": "map",
          "%keyType": "string",
          "%itemType": "string",
          "%fieldNumber": 4
        },
        {
          "%name": "credit_card",
          "%type": "string",
          "%oneof": "payment_method",
          "%fieldNumber": 20
        },
        {
          "%name": "paypal_email",
          "%type": "string",
          "%oneof": "payment_method",
          "%fieldNumber": 21
        }
      ]
    }
  }
}
```

---

## Appendix B: Proto2 vs Proto3 Feature Matrix

| Feature | Proto2 | Proto3 | USDL Directive | Notes |
|---------|--------|--------|----------------|-------|
| **Syntax Declaration** | `syntax = "proto2";` | `syntax = "proto3";` | `%version: "proto2"` or `"proto3"` | Default: proto3 |
| **Required Fields** | ✅ `required string x = 1;` | ❌ (all optional) | `%required: true` | Proto3 simplified |
| **Optional Fields** | ✅ `optional string x = 1;` | Implicit | `%required: false` (default) | Proto3 no label |
| **Default Values** | ✅ `[default = 42]` | ❌ (zero values) | `%default: 42` | Proto2 only |
| **Field Presence** | ✅ `has_x()` method | ❌ (implicit via zero) | N/A | Proto3 breaking change |
| **Repeated Fields** | ✅ `repeated int32 x = 1;` | ✅ `repeated int32 x = 1;` | `%array: true` | Both versions |
| **Packed Repeated** | Opt-in `[packed=true]` | Default (opt-out) | `%packed: true` | Proto3 optimization |
| **Maps** | Via repeated groups | ✅ Native `map<K,V>` | `%map: true` | Proto3 syntax sugar |
| **Oneof** | ✅ Supported | ✅ Supported | `%oneof: "group_name"` | Both versions |
| **Enums** | First = 0 required | First = 0 required | `%ordinal: 0` (first) | Both versions |
| **Extensions** | ✅ Supported | ❌ Removed | N/A | Legacy feature |
| **Groups** | ✅ Deprecated | ❌ Removed | N/A | Don't use |
| **Unknown Fields** | ✅ Preserved | ✅ Preserved (3.5+) | N/A | Compatibility |
| **JSON Mapping** | ❌ No standard | ✅ Standard | N/A | Proto3 feature |
| **Any Type** | ❌ | ✅ `google.protobuf.Any` | N/A | Proto3 well-known type |

---

**END OF DOCUMENT**

---

## Document Metadata

**Version:** 1.0
**Status:** Draft - Ready for Review
**Approval Required From:** UTL-X Core Team, Project Lead
**Next Steps:**
1. Review findings and prioritize implementation
2. Compare with Avro and Parquet studies to determine order
3. Approve Phase 1 implementation start (14-16 days estimated)

**Related Documents:**
- [Avro Integration Study](avro-integration-study.md) - 12-16 days effort
- [Parquet Integration Study](parquet-integration-study.md) - 24-30 days effort
- [USDL 1.0 Specification](../language-guide/universal-schema-dsl.md)
- [Format Module Architecture](../architecture/format-modules.md)

**Recommended Implementation Priority:**
1. **Avro** (12-16 days) - Simpler, high value for Kafka/streaming
2. **Protobuf** (24-29 days) - High strategic value for gRPC/microservices
3. **Parquet** (24-30 days) - High value for data lakes/analytics

**Total Estimated Effort (All Three):** 60-75 days for schema support across all three formats

# Apache Parquet Integration Study

**Document Type:** Technical Feasibility Study
**Author:** UTL-X Project Team
**Date:** 2025-10-27
**Status:** Draft
**Related:** [Avro Integration Study](avro-integration-study.md), [USDL 1.0 Specification](../language-guide/universal-schema-dsl.md)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Apache Parquet Overview](#apache-parquet-overview)
3. [Current UTL-X Architecture Analysis](#current-utlx-architecture-analysis)
4. [Parquet Integration Architecture](#parquet-integration-architecture)
5. [USDL to Parquet Schema Mapping](#usdl-to-parquet-schema-mapping)
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

### Recommendation: **Proceed with Caution** (Higher Complexity than Avro)

Apache Parquet integration is **feasible** but **more complex** than Avro integration due to Parquet's columnar storage model and tight coupling with binary file formats.

### Key Findings

✅ **USDL 1.0 Already Defines Parquet Directives** (Tier 3):
- `%logicalType` - Semantic type annotations (shared with Avro)
- `%repetition` - Parquet-specific: required, optional, repeated
- `%encoding` - Parquet encoding types (PLAIN, RLE, DELTA, etc.)
- `%compression` - Compression algorithms (snappy, gzip, lzo, zstd)
- `%precision` / `%scale` - Decimal type support (shared with Avro)

⚠️ **Complexity Factors**:
- Parquet is primarily a **columnar file format**, not just a schema language
- Schema is embedded in `.parquet` files, not standalone
- Requires Apache Arrow or Parquet libraries for schema manipulation
- UTL-X would need to decide: schema-only or full read/write support?

### Effort Estimation

| Scope | Effort | Priority |
|-------|--------|----------|
| **Schema Metadata Extraction** (parse .parquet → UDM) | 3-5 days | High |
| **Schema Serialization via USDL** (USDL → Parquet schema object) | 5-7 days | High |
| **Data Reading** (Parquet file → UDM data) | 5-7 days | Medium |
| **Data Writing** (UDM data → Parquet file) | 5-7 days | Medium |
| **Testing & Documentation** | 3-4 days | High |
| **CLI Integration** | 1 day | High |

**Total Effort:**
- **Schema-Only MVP:** 8-12 days (metadata extraction + USDL serialization)
- **Full Read/Write Support:** 22-30 days (includes data transformation)

### Recommended Approach

**Phase 1 (MVP):** Schema metadata support only
- Parse Parquet schema from `.parquet` files → UDM
- Generate Parquet schema objects from USDL (in-memory, not written to files yet)
- Focus on Big Data integration use case (schema registry, documentation)

**Phase 2 (Future):** Full data transformation
- Read Parquet files → UDM data
- Write UDM data → Parquet files
- Integrate with data pipeline use cases (ETL, Spark, Hive)

---

## 2. Apache Parquet Overview

### What is Apache Parquet?

Apache Parquet is an **open-source columnar storage file format** designed for efficient data storage and retrieval in Big Data systems.

**Key Characteristics:**
- **Columnar Storage:** Data organized by column, not row (optimized for analytics)
- **Compression-Friendly:** High compression ratios due to similar data in columns
- **Schema Evolution:** Supports adding/removing columns over time
- **Predicate Pushdown:** Filter data before reading entire file
- **Ecosystem Integration:** Native support in Spark, Hive, Impala, Presto, Drill, Arrow

### Parquet vs Avro

| Feature | Avro | Parquet |
|---------|------|---------|
| **Storage Model** | Row-based | Column-based |
| **Best For** | Serialization, streaming | Analytics, OLAP queries |
| **Read Pattern** | Read entire record | Read specific columns |
| **Write Speed** | Fast | Moderate (columnar organization overhead) |
| **Read Speed (all cols)** | Fast | Moderate |
| **Read Speed (few cols)** | Moderate | Very Fast (only read needed columns) |
| **Compression** | Good | Excellent (similar values in columns) |
| **Schema Storage** | Separate file or embedded | Embedded in footer |
| **Use Cases** | Kafka, message queues, logs | Data lakes, warehouses, OLAP |

### Parquet Schema Model

Parquet uses a **nested data model** similar to Protocol Buffers and Avro:

**Primitive Types:**
- `BOOLEAN`, `INT32`, `INT64`, `INT96` (deprecated), `FLOAT`, `DOUBLE`
- `BYTE_ARRAY` (variable length), `FIXED_LEN_BYTE_ARRAY`

**Logical Types (Annotations):**
- `STRING`, `ENUM`, `UUID`
- `DECIMAL` (fixed or variable precision)
- `DATE`, `TIME_MILLIS`, `TIME_MICROS`, `TIMESTAMP_MILLIS`, `TIMESTAMP_MICROS`
- `JSON`, `BSON`
- `LIST`, `MAP`

**Repetition Levels:**
- `REQUIRED` - Field must be present (like Avro non-nullable)
- `OPTIONAL` - Field may be absent (like Avro nullable union)
- `REPEATED` - Array of values (like Avro array)

**Example Parquet Schema (Conceptual):**
```
message Order {
  required int64 order_id;
  optional binary customer_name (STRING);
  optional int32 order_date (DATE);
  repeated group items {
    required binary sku (STRING);
    required int32 quantity;
    required double price (DECIMAL(10,2));
  }
}
```

### Parquet Ecosystem

**Write Parquet:**
- Apache Spark (Scala, Python, Java)
- Apache Hive
- Apache Arrow (C++, Python, Rust)
- Pandas (Python)
- Dask (Python)
- Polars (Rust, Python)

**Read Parquet:**
- All writers + Impala, Presto, Drill, Athena, BigQuery, Snowflake, Databricks

**Schema Registries:**
- Confluent Schema Registry (supports Avro + Protobuf, **not Parquet**)
- AWS Glue Data Catalog (Parquet metadata)
- Apache Hive Metastore (Parquet table schemas)

---

## 3. Current UTL-X Architecture Analysis

### Existing Format Support

**Current Implementation:**

| Format | Parser | Serializer | Schema Support | Status |
|--------|--------|------------|----------------|--------|
| XML | ✅ | ✅ | XSD ✅ | Stable |
| JSON | ✅ | ✅ | JSON Schema ✅ | Stable |
| CSV | ✅ | ✅ | ❌ | Stable |
| YAML | ✅ | ✅ | ❌ | Stable |
| **Avro** | ❌ | ❌ | Schema ⏳ | Planned |
| **Parquet** | ❌ | ❌ | Schema ⏳ | Study Phase |

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
└── parquet/       # ← NEW MODULE
    ├── ParquetSchemaParser.kt      # Parse .parquet → schema UDM
    ├── ParquetSchemaSerializer.kt  # USDL → Parquet schema object
    ├── ParquetDataParser.kt        # (Phase 2) .parquet → data UDM
    └── ParquetDataSerializer.kt    # (Phase 2) UDM → .parquet
```

### Schema Serializer Pattern (From XSD/JSON Schema)

Both existing schema serializers follow this pattern:

```kotlin
class SchemaSerializer(
    private val version: String,
    private val prettyPrint: Boolean = true
) {
    enum class SerializationMode {
        LOW_LEVEL,      // User provides native schema structure
        UNIVERSAL_DSL   // User provides USDL
    }

    fun serialize(udm: UDM): String {
        val mode = detectMode(udm)
        val schemaStructure = when (mode) {
            SerializationMode.UNIVERSAL_DSL -> transformUniversalDSL(udm)
            SerializationMode.LOW_LEVEL -> udm
        }
        validateSchema(schemaStructure)
        return renderSchema(schemaStructure)
    }
}
```

**For Parquet:** This pattern needs adaptation because:
1. Parquet schemas are **embedded in binary files**, not standalone text
2. Would need to return a `ParquetSchema` object, not a String
3. Final output requires Parquet library to write binary footer

---

## 4. Parquet Integration Architecture

### Design Decision: Schema-Only MVP

**Rationale:**
- Parquet is primarily a **file format**, not a schema language like XSD/JSON Schema
- Full Parquet support requires complex binary I/O (columnar encoding, compression, etc.)
- Schema metadata extraction has clear value (documentation, data catalog integration)
- Decouples schema support from data transformation concerns

### Proposed Architecture (Phase 1 - Schema Only)

```kotlin
package org.apache.utlx.formats.parquet

import org.apache.utlx.core.udm.UDM
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.Types
import java.io.File

/**
 * Parquet Schema Parser - Extracts schema from .parquet files
 */
class ParquetSchemaParser {
    /**
     * Parse Parquet file schema to UDM representation
     */
    fun parseSchema(parquetFile: File): UDM {
        // Use Parquet library to read schema from file footer
        val reader = ParquetFileReader.open(HadoopInputFile.fromPath(
            Path(parquetFile.absolutePath), Configuration()
        ))
        val schema = reader.footer.fileMetaData.schema
        return convertToUDM(schema)
    }

    private fun convertToUDM(schema: MessageType): UDM {
        // Convert Parquet MessageType → UDM.Object
        // Map Parquet types → USDL directives
    }
}

/**
 * Parquet Schema Serializer - Generates Parquet schema from USDL
 */
class ParquetSchemaSerializer(
    private val compression: String = "SNAPPY"
) {
    enum class SerializationMode {
        LOW_LEVEL,      // User provides Parquet schema structure
        UNIVERSAL_DSL   // User provides USDL
    }

    /**
     * Generate Parquet MessageType from UDM (USDL or low-level)
     */
    fun generateSchema(udm: UDM): MessageType {
        val mode = detectMode(udm)
        val schemaStructure = when (mode) {
            SerializationMode.UNIVERSAL_DSL -> transformUniversalDSL(udm as UDM.Object)
            SerializationMode.LOW_LEVEL -> udm
        }
        return buildParquetSchema(schemaStructure)
    }

    /**
     * Transform USDL to Parquet schema representation
     */
    private fun transformUniversalDSL(schema: UDM.Object): UDM {
        // Extract USDL directives
        val types = schema.properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL requires '%types' directive")

        // Build Parquet-compatible schema structure
        val fields = mutableListOf<ParquetField>()

        types.properties.forEach { (typeName, typeDef) ->
            if (typeDef !is UDM.Object) return@forEach
            val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String

            if (kind == "structure") {
                val usdlFields = typeDef.properties["%fields"] as? UDM.Array ?: return@forEach
                usdlFields.elements.forEach { fieldUdm ->
                    fields.add(convertUSDLField(fieldUdm as UDM.Object))
                }
            }
        }

        return buildSchemaUDM(fields)
    }

    private fun buildParquetSchema(udm: UDM): MessageType {
        // Convert UDM → Parquet MessageType using parquet-mr library
        val builder = Types.buildMessage()

        // Add fields from UDM
        extractFields(udm).forEach { field ->
            builder.addField(createParquetType(field))
        }

        return builder.named("utlx_generated")
    }
}
```

### CLI Integration

**Schema Extraction:**
```bash
# Extract schema from Parquet file → JSON
utlx schema extract data.parquet -o schema.json

# Extract schema → USDL
utlx schema extract data.parquet --format usdl -o schema.usdl.json

# Extract schema → XSD (via USDL transformation)
utlx schema convert data.parquet --to xsd -o schema.xsd
```

**Schema Generation (Future - Phase 2):**
```bash
# Generate Parquet file from USDL + CSV data
utlx transform mapping.utlx data.csv -o output.parquet
```

### Output Format Options

**Option 1: Schema as JSON (Parquet JSON representation)**
```json
{
  "name": "Order",
  "type": "record",
  "fields": [
    {
      "name": "order_id",
      "type": "int64",
      "repetitionType": "REQUIRED"
    },
    {
      "name": "customer_name",
      "type": {
        "type": "byte_array",
        "logicalType": "STRING"
      },
      "repetitionType": "OPTIONAL"
    }
  ]
}
```

**Option 2: Schema as UDM (for transformation pipelines)**
```json
{
  "%types": {
    "Order": {
      "%kind": "structure",
      "%fields": [
        {
          "%name": "order_id",
          "%type": "integer",
          "%repetition": "required"
        },
        {
          "%name": "customer_name",
          "%type": "string",
          "%repetition": "optional"
        }
      ]
    }
  }
}
```

---

## 5. USDL to Parquet Schema Mapping

### Primitive Type Mapping

| USDL Type | Parquet Physical Type | Parquet Logical Type | Notes |
|-----------|------------------------|----------------------|-------|
| `string` | `BYTE_ARRAY` | `STRING` | UTF-8 encoded |
| `integer` | `INT32` | - | 32-bit signed int |
| `integer` (%size=64) | `INT64` | - | 64-bit signed int |
| `number` | `DOUBLE` | - | 64-bit float |
| `boolean` | `BOOLEAN` | - | Single bit |
| `date` | `INT32` | `DATE` | Days since epoch |
| `timestamp` | `INT64` | `TIMESTAMP_MILLIS` | Milliseconds since epoch |

### Logical Type Mapping

| USDL Directive | Parquet Equivalent |
|----------------|---------------------|
| `%logicalType: "date"` | Physical: `INT32`, Logical: `DATE` |
| `%logicalType: "timestamp-millis"` | Physical: `INT64`, Logical: `TIMESTAMP_MILLIS` |
| `%logicalType: "timestamp-micros"` | Physical: `INT64`, Logical: `TIMESTAMP_MICROS` |
| `%logicalType: "decimal"` + `%precision` + `%scale` | Physical: `BYTE_ARRAY`, Logical: `DECIMAL` |
| `%logicalType: "uuid"` | Physical: `FIXED_LEN_BYTE_ARRAY[16]`, Logical: `UUID` |
| `%logicalType: "json"` | Physical: `BYTE_ARRAY`, Logical: `JSON` |

### Repetition Mapping

| USDL Directive | Parquet Repetition | Notes |
|----------------|---------------------|-------|
| `%required: true` | `REQUIRED` | Field must be present |
| `%required: false` (default) | `OPTIONAL` | Field may be null |
| `%array: true` | `REPEATED` | Array of values |

### Encoding Hints

| USDL Directive | Parquet Encoding | Notes |
|----------------|------------------|-------|
| `%encoding: "plain"` | `PLAIN` | No encoding |
| `%encoding: "rle"` | `RLE` | Run-length encoding |
| `%encoding: "delta"` | `DELTA_BINARY_PACKED` | Delta encoding for integers |
| `%encoding: "dictionary"` | `PLAIN_DICTIONARY` | Dictionary encoding |

### Compression Mapping

| USDL Directive | Parquet Compression |
|----------------|---------------------|
| `%compression: "snappy"` | `SNAPPY` (default) |
| `%compression: "gzip"` | `GZIP` |
| `%compression: "lzo"` | `LZO` |
| `%compression: "zstd"` | `ZSTD` |
| `%compression: "uncompressed"` | `UNCOMPRESSED` |

### Complete Example: USDL → Parquet Schema

**Input: USDL**
```json
{
  "%types": {
    "Order": {
      "%kind": "structure",
      "%documentation": "Customer order record",
      "%compression": "snappy",
      "%fields": [
        {
          "%name": "order_id",
          "%type": "integer",
          "%required": true,
          "%description": "Unique order identifier"
        },
        {
          "%name": "order_date",
          "%type": "integer",
          "%logicalType": "date",
          "%required": true
        },
        {
          "%name": "customer_name",
          "%type": "string",
          "%required": false
        },
        {
          "%name": "total",
          "%type": "number",
          "%logicalType": "decimal",
          "%precision": 10,
          "%scale": 2,
          "%required": true
        },
        {
          "%name": "items",
          "%type": "structure",
          "%array": true,
          "%fields": [
            {
              "%name": "sku",
              "%type": "string",
              "%required": true,
              "%encoding": "dictionary"
            },
            {
              "%name": "quantity",
              "%type": "integer",
              "%required": true,
              "%encoding": "delta"
            },
            {
              "%name": "price",
              "%type": "number",
              "%logicalType": "decimal",
              "%precision": 10,
              "%scale": 2,
              "%required": true
            }
          ]
        }
      ]
    }
  }
}
```

**Output: Parquet Schema (Conceptual - would be MessageType object)**
```
message Order {
  required int64 order_id;
  required int32 order_date (DATE);
  optional binary customer_name (STRING);
  required fixed_len_byte_array(5) total (DECIMAL(10,2));
  repeated group items {
    required binary sku (STRING);
    required int32 quantity;
    required fixed_len_byte_array(5) price (DECIMAL(10,2));
  }
}

// Compression: SNAPPY (applied to entire file)
// Encoding hints: sku (DICTIONARY), quantity (DELTA)
```

---

## 6. Implementation Plan

### Phase 1: Schema Metadata Support (MVP)

**Goal:** Enable schema extraction from Parquet files and USDL-based schema generation

#### 1.1 Create Format Module (1 day)
- Create `formats/parquet/` directory structure
- Add Gradle build configuration
- Add Parquet library dependencies

#### 1.2 Implement ParquetSchemaParser (3-4 days)
- Read Parquet file footer using `parquet-mr` library
- Extract `MessageType` schema object
- Convert Parquet types → UDM representation
- Map repetition levels → `%required`, `%array`, `%nullable`
- Map logical types → `%logicalType`
- Handle nested groups (structs within structs)
- Handle repeated groups (arrays of structs)

**Test Cases:**
- Simple flat schema (primitives only)
- Schema with logical types (date, timestamp, decimal, uuid)
- Nested structure (group within group)
- Repeated fields (arrays)
- Complex nested arrays

#### 1.3 Implement ParquetSchemaSerializer (4-5 days)
- Implement USDL detection logic
- Transform USDL directives → Parquet schema structure
- Build `MessageType` using Parquet's `Types` builder API
- Map USDL types → Parquet physical/logical types
- Handle `%repetition`, `%encoding`, `%compression` directives
- Generate valid Parquet schema objects

**Test Cases:**
- USDL with primitives → Parquet schema
- USDL with logical types → Parquet schema
- USDL with nested structures → Parquet nested groups
- USDL with arrays → Parquet repeated fields
- USDL with compression/encoding hints

#### 1.4 CLI Integration (1 day)
- Add `utlx schema extract <file.parquet>` command
- Add format detection for `.parquet` files
- Add output format options (json, usdl, xsd)
- Add pretty-printing options

#### 1.5 Testing & Documentation (2-3 days)
- 50+ unit tests (schema parsing, USDL transformation)
- 20+ integration tests (real Parquet files)
- Conformance test suite
- Documentation updates

**Phase 1 Total: 11-14 days**

---

### Phase 2: Data Transformation Support (Future)

**Goal:** Enable reading/writing Parquet data files via UTL-X transformations

#### 2.1 Implement ParquetDataParser (5-6 days)
- Read Parquet file using Arrow or Parquet reader
- Convert columnar data → row-based UDM representation
- Handle projection (read only needed columns)
- Handle predicate pushdown (filter before reading)
- Stream large files (don't load entire dataset into memory)

#### 2.2 Implement ParquetDataSerializer (5-7 days)
- Convert UDM data → columnar format
- Write Parquet files with proper encoding/compression
- Handle partitioning (write multiple files)
- Optimize for Spark/Hive compatibility

#### 2.3 Transformation Support (2-3 days)
- Enable `input parquet` in UTL-X transformations
- Enable `output parquet` in UTL-X transformations
- Add Parquet-specific options (compression, encoding)

#### 2.4 Testing & Documentation (3-4 days)
- Data transformation tests
- Large file tests (memory efficiency)
- Spark/Hive compatibility tests

**Phase 2 Total: 15-20 days**

---

## 7. Effort Estimation

### Detailed Breakdown

| Component | Complexity | Effort (days) | Priority | Dependencies |
|-----------|------------|---------------|----------|--------------|
| **Module Setup** | Low | 1 | High | - |
| **ParquetSchemaParser** | Medium-High | 3-4 | High | parquet-mr library |
| **ParquetSchemaSerializer** | Medium-High | 4-5 | High | USDL10.kt, parquet-mr |
| **CLI Integration** | Low | 1 | High | CLI module |
| **Unit Tests** | Medium | 2 | High | - |
| **Conformance Tests** | Medium | 1 | High | Test data |
| **Documentation** | Low | 1 | High | - |
| **ParquetDataParser** | High | 5-6 | Medium | Phase 1 complete |
| **ParquetDataSerializer** | High | 5-7 | Medium | Phase 1 complete |
| **Data Transform Tests** | High | 3-4 | Medium | Phase 2 parsers |

### Effort Summary

**Phase 1 (Schema Only - MVP):**
- **Core Implementation:** 8-9 days
- **Testing & Documentation:** 3-4 days
- **Total:** **11-13 days**

**Phase 2 (Data Transformation):**
- **Core Implementation:** 10-13 days
- **Testing & Documentation:** 3-4 days
- **Total:** **13-17 days**

**Full Implementation:** **24-30 days**

### Comparison with Other Integrations

| Format | Schema Support | Data Support | Total Effort | Status |
|--------|----------------|--------------|--------------|--------|
| XSD | 5 days | N/A (XML handles data) | 5 days | ✅ Complete |
| JSON Schema | 4 days | N/A (JSON handles data) | 4 days | ✅ Complete |
| Avro | 9-12 days | 3-4 days | 12-16 days | ⏳ Planned |
| **Parquet** | **11-13 days** | **13-17 days** | **24-30 days** | 📋 Study Phase |

**Parquet is 2x more complex than Avro** due to:
- Columnar storage model (requires different algorithms)
- Nested schema complexity (groups, repetition levels)
- Binary file format (requires Parquet/Arrow libraries)
- Performance considerations (columnar encoding, compression)

---

## 8. Comparison Matrix

### Schema Language Comparison

| Feature | Parquet | Avro | XSD | JSON Schema |
|---------|---------|------|-----|-------------|
| **Schema Type** | Embedded | Separate/Embedded | Standalone | Standalone |
| **Data Model** | Nested | Nested | Nested | Nested |
| **Primitive Types** | 7 types | 6 types | 44 types | 7 types |
| **Logical Types** | 10+ types | 10+ types | Via patterns | Via format |
| **Nullable Fields** | Optional repetition | Union with null | minOccurs=0 | nullable keyword |
| **Arrays** | Repeated repetition | Array type | maxOccurs | array type |
| **Nested Structs** | Group type | Record type | complexType | object type |
| **Schema Evolution** | Add columns | Aliases, defaults | Extension | No standard |
| **Versioning** | Implicit | Explicit | namespace | $schema URI |
| **Comments** | Via metadata | Via doc | xs:annotation | description |

### Use Case Comparison

| Use Case | Best Format | Reason |
|----------|-------------|--------|
| **Kafka Messages** | Avro | Row-based, fast serialization |
| **Data Lake Storage** | **Parquet** | Columnar, high compression, query optimization |
| **REST API Schemas** | JSON Schema | Web-native, tooling |
| **SOAP/Enterprise** | XSD | XML ecosystem, mature tooling |
| **Analytics Queries** | **Parquet** | Column pruning, predicate pushdown |
| **Spark/Hive Tables** | **Parquet** | Native integration, partition support |
| **Stream Processing** | Avro | Fast read/write, schema registry |
| **Log Aggregation** | Avro | Fast append, schema evolution |

### Performance Characteristics

| Metric | Parquet | Avro |
|--------|---------|------|
| **Write Speed** | Moderate (columnar encoding) | Fast |
| **Read Speed (all columns)** | Moderate | Fast |
| **Read Speed (few columns)** | **Very Fast** (column pruning) | Moderate |
| **File Size** | **Small** (high compression) | Medium |
| **Memory Usage** | Low (columnar streaming) | Medium (row-based) |
| **Query Performance** | **Excellent** (OLAP) | Good (OLTP) |

---

## 9. Benefits & Use Cases

### 9.1 Use Case: Data Lake Schema Documentation

**Scenario:** Large data lake with hundreds of Parquet datasets, need unified schema documentation

**Solution:**
```bash
# Extract schemas from all Parquet files
find /data/lake -name "*.parquet" | while read file; do
  utlx schema extract "$file" --format usdl -o schemas/$(basename "$file" .parquet).json
done

# Generate HTML documentation from USDL
utlx docs generate schemas/ -o data-catalog.html
```

**Benefits:**
- Automatic schema extraction from existing Parquet files
- Unified documentation format across formats
- Schema evolution tracking via git diffs

### 9.2 Use Case: Parquet ↔ XSD Schema Conversion

**Scenario:** Legacy XML systems need to interoperate with modern data lake (Parquet storage)

**Solution:**
```bash
# Extract Parquet schema → USDL
utlx schema extract sales.parquet --format usdl -o sales-usdl.json

# Transform USDL → XSD for legacy system
utlx transform usdl-to-xsd.utlx sales-usdl.json -o sales.xsd

# Reverse: XSD → Parquet schema (via USDL)
utlx schema convert legacy-sales.xsd --to parquet -o sales-schema.json
```

**Benefits:**
- Bridge legacy XML systems with modern data lakes
- Single source of truth (USDL) for both formats
- No manual schema translation

### 9.3 Use Case: Spark Schema Generation

**Scenario:** Generate Spark DDL from business-friendly USDL definitions

**USDL Input (CSV format):**
```csv
Field,Type,LogicalType,Required,Description
order_id,integer,,true,Unique order ID
order_date,integer,date,true,Order date
customer_name,string,,false,Customer name
total,number,decimal,true,Order total
```

**Transformation:**
```utlx
%utlx 1.0
input csv { headers: true }
output parquet %usdl 1.0
---
{
  %types: {
    Order: {
      %kind: "structure",
      %compression: "snappy",
      %fields: $input.rows |> map(row => {
        %name: row.Field,
        %type: row.Type,
        %logicalType: row.LogicalType,
        %required: row.Required == "true",
        %description: row.Description
      })
    }
  }
}
```

**Output:** Parquet schema object for Spark DataFrame creation

**Benefits:**
- Business analysts can define schemas in Excel/CSV
- Automatic validation via USDL
- Generate Parquet-optimized schemas for production

### 9.4 Use Case: AWS Glue Data Catalog Integration

**Scenario:** Sync Parquet schemas to AWS Glue Data Catalog for Athena/Redshift Spectrum queries

**Solution:**
```bash
# Extract Parquet schema
utlx schema extract s3://bucket/data/orders/ --format json -o order-schema.json

# Transform to Glue-compatible JSON
utlx transform parquet-to-glue.utlx order-schema.json -o glue-schema.json

# Upload to Glue (via AWS CLI)
aws glue create-table --database-name analytics --table-input file://glue-schema.json
```

**Benefits:**
- Automated schema discovery for Glue catalogs
- Schema evolution tracking
- Multi-format support (Parquet, Avro, JSON)

### 9.5 Use Case: Parquet Schema Validation

**Scenario:** Validate that produced Parquet files conform to expected schema

**Solution:**
```bash
# Define expected schema in USDL
cat > expected-schema.json <<EOF
{
  "%types": {
    "Order": {
      "%kind": "structure",
      "%fields": [
        {"%name": "order_id", "%type": "integer", "%required": true},
        {"%name": "customer_name", "%type": "string", "%required": true}
      ]
    }
  }
}
EOF

# Extract actual schema from Parquet file
utlx schema extract output.parquet --format usdl -o actual-schema.json

# Validate schemas match
utlx schema validate actual-schema.json expected-schema.json
```

**Benefits:**
- CI/CD schema validation
- Catch schema drift early
- Enforce data quality standards

---

## 10. Technical Risks & Mitigations

### Risk 1: Parquet Library Complexity

**Risk:** Apache Parquet libraries (parquet-mr, Arrow) are complex with many dependencies

**Impact:** Medium - Increased build time, potential version conflicts

**Mitigation:**
- Use official Apache Parquet library (parquet-mr) - mature and well-tested
- Isolate Parquet dependencies in separate Gradle module
- Use Gradle dependency management to avoid conflicts
- Test with multiple Parquet library versions

### Risk 2: Columnar Storage Model Mismatch

**Risk:** UTL-X UDM is row-based, Parquet is columnar - impedance mismatch for data transformation

**Impact:** High - Complex conversion logic, potential performance issues

**Mitigation:**
- **Phase 1:** Schema-only support (no data transformation) - avoids this issue
- **Phase 2:** Use Apache Arrow as intermediary (columnar in-memory format)
- Consider streaming conversion (row batches → column chunks)
- Document performance characteristics and limitations

### Risk 3: Memory Usage for Large Files

**Risk:** Large Parquet files (GBs) may exceed available memory

**Impact:** High - OOM errors, poor performance

**Mitigation:**
- Schema extraction only reads footer (< 1 MB regardless of file size)
- Phase 2: Use streaming APIs (read/write in batches)
- Document memory requirements for data transformation
- Add CLI options for batch size tuning

### Risk 4: Schema Evolution Complexity

**Risk:** Parquet schema evolution (column addition/removal) is complex

**Impact:** Medium - May not support all evolution patterns

**Mitigation:**
- Phase 1 focuses on schema structure, not evolution semantics
- Document supported evolution patterns
- Future: Add schema diff/merge tools

### Risk 5: Ecosystem Integration

**Risk:** Parquet files generated by UTL-X may not be compatible with Spark/Hive/Athena

**Impact:** High - Breaks primary use case

**Mitigation:**
- Use official Parquet libraries (ensures compatibility)
- Test with Spark, Hive, Presto, Athena
- Follow Parquet format specifications strictly
- Add compatibility test suite

### Risk 6: Encoding/Compression Optimization

**Risk:** Naive encoding/compression choices may result in poor performance or large files

**Impact:** Medium - Suboptimal but functional

**Mitigation:**
- Use sensible defaults (SNAPPY compression, PLAIN encoding)
- Document encoding/compression options
- Add benchmarking tests
- Future: Add auto-tuning based on data profiling

---

## 11. Testing Strategy

### 11.1 Unit Tests (Schema Support)

**ParquetSchemaParser Tests (15 tests):**
```kotlin
@Test
fun `parse simple flat schema`() {
    val parquetFile = File("test-data/simple-order.parquet")
    val udm = parser.parseSchema(parquetFile)

    val types = (udm as UDM.Object).properties["%types"] as UDM.Object
    types.properties shouldContainKey "Order"
}

@Test
fun `parse schema with logical types`() {
    val parquetFile = File("test-data/logical-types.parquet")
    val udm = parser.parseSchema(parquetFile)

    val fields = extractFields(udm)
    fields.find { it.name == "order_date" }!!.logicalType shouldBe "date"
    fields.find { it.name == "total" }!!.logicalType shouldBe "decimal"
}

@Test
fun `parse nested group structure`() {
    val parquetFile = File("test-data/nested-order.parquet")
    val udm = parser.parseSchema(parquetFile)

    val fields = extractFields(udm)
    val itemsField = fields.find { it.name == "items" }!!
    itemsField.type shouldBe "structure"
    itemsField.array shouldBe true
}

@Test
fun `parse repeated fields`() {
    val parquetFile = File("test-data/array-fields.parquet")
    val udm = parser.parseSchema(parquetFile)

    val fields = extractFields(udm)
    fields.find { it.name == "tags" }!!.array shouldBe true
}

@Test
fun `handle required vs optional repetition`() {
    val parquetFile = File("test-data/repetition.parquet")
    val udm = parser.parseSchema(parquetFile)

    val fields = extractFields(udm)
    fields.find { it.name == "order_id" }!!.required shouldBe true
    fields.find { it.name == "customer_name" }!!.required shouldBe false
}
```

**ParquetSchemaSerializer Tests (20 tests):**
```kotlin
@Test
fun `USDL structure with primitives`() {
    val usdl = """
    {
      "%types": {
        "Order": {
          "%kind": "structure",
          "%fields": [
            {"%name": "order_id", "%type": "integer", "%required": true},
            {"%name": "customer_name", "%type": "string", "%required": false}
          ]
        }
      }
    }
    """.trimIndent()

    val messageType = serializer.generateSchema(parseJSON(usdl))

    messageType.name shouldBe "Order"
    messageType.fields.size shouldBe 2
    messageType.getType("order_id").repetition shouldBe REQUIRED
    messageType.getType("customer_name").repetition shouldBe OPTIONAL
}

@Test
fun `USDL with logical type date`() {
    val usdl = """
    {
      "%types": {
        "Event": {
          "%kind": "structure",
          "%fields": [{
            "%name": "event_date",
            "%type": "integer",
            "%logicalType": "date",
            "%required": true
          }]
        }
      }
    }
    """.trimIndent()

    val messageType = serializer.generateSchema(parseJSON(usdl))
    val dateField = messageType.getType("event_date")

    dateField.asPrimitiveType().primitiveTypeName shouldBe INT32
    dateField.logicalTypeAnnotation shouldBe LogicalTypeAnnotation.dateType()
}

@Test
fun `USDL with decimal precision and scale`() {
    val usdl = """
    {
      "%types": {
        "Product": {
          "%kind": "structure",
          "%fields": [{
            "%name": "price",
            "%type": "number",
            "%logicalType": "decimal",
            "%precision": 10,
            "%scale": 2,
            "%required": true
          }]
        }
      }
    }
    """.trimIndent()

    val messageType = serializer.generateSchema(parseJSON(usdl))
    val priceField = messageType.getType("price")

    val decimalAnnotation = priceField.logicalTypeAnnotation as DecimalLogicalTypeAnnotation
    decimalAnnotation.precision shouldBe 10
    decimalAnnotation.scale shouldBe 2
}

@Test
fun `USDL with nested structure`() {
    val usdl = """
    {
      "%types": {
        "Order": {
          "%kind": "structure",
          "%fields": [
            {"%name": "order_id", "%type": "integer", "%required": true},
            {
              "%name": "items",
              "%type": "structure",
              "%array": true,
              "%fields": [
                {"%name": "sku", "%type": "string", "%required": true},
                {"%name": "quantity", "%type": "integer", "%required": true}
              ]
            }
          ]
        }
      }
    }
    """.trimIndent()

    val messageType = serializer.generateSchema(parseJSON(usdl))

    val itemsField = messageType.getType("items") as GroupType
    itemsField.repetition shouldBe REPEATED
    itemsField.fields.size shouldBe 2
}

@Test
fun `USDL with encoding hints`() {
    val usdl = """
    {
      "%types": {
        "Product": {
          "%kind": "structure",
          "%fields": [{
            "%name": "category",
            "%type": "string",
            "%required": true,
            "%encoding": "dictionary"
          }]
        }
      }
    }
    """.trimIndent()

    val messageType = serializer.generateSchema(parseJSON(usdl))

    // Encoding hints are metadata, not part of schema structure
    // Would need separate API to access encoding configuration
    messageType shouldNotBe null
}
```

### 11.2 Conformance Tests (Parquet Files)

**Test Data Generation:**
```python
# generate-test-parquet-files.py
import pyarrow as pa
import pyarrow.parquet as pq
import pandas as pd

# Test 1: Simple flat schema
schema1 = pa.schema([
    ('order_id', pa.int64()),
    ('customer_name', pa.string()),
    ('total', pa.float64())
])
table1 = pa.table({'order_id': [1, 2], 'customer_name': ['Alice', 'Bob'], 'total': [100.0, 200.0]}, schema=schema1)
pq.write_table(table1, 'test-data/simple-order.parquet')

# Test 2: Logical types
schema2 = pa.schema([
    ('order_date', pa.date32()),
    ('order_time', pa.timestamp('ms')),
    ('total', pa.decimal128(10, 2)),
    ('transaction_id', pa.binary(16))  # UUID as fixed bytes
])
table2 = pa.table({...}, schema=schema2)
pq.write_table(table2, 'test-data/logical-types.parquet')

# Test 3: Nested structures
schema3 = pa.schema([
    ('order_id', pa.int64()),
    ('items', pa.list_(pa.struct([
        ('sku', pa.string()),
        ('quantity', pa.int32()),
        ('price', pa.float64())
    ])))
])
table3 = pa.table({...}, schema=schema3)
pq.write_table(table3, 'test-data/nested-order.parquet')
```

**Conformance Test Suite (10 tests):**
```kotlin
class ParquetConformanceTests {
    @Test
    fun `parse simple_order parquet file`() {
        val udm = parser.parseSchema(File("test-data/simple-order.parquet"))
        // Assertions...
    }

    @Test
    fun `roundtrip: Parquet → USDL → Parquet → compare schemas`() {
        val original = File("test-data/simple-order.parquet")
        val usdl = parser.parseSchema(original)
        val regenerated = serializer.generateSchema(usdl)

        // Compare original and regenerated schemas (structure, not metadata)
        compareSchemas(readSchema(original), regenerated) shouldBe true
    }

    @Test
    fun `generated parquet readable by PySpark`() {
        // Generate Parquet file from USDL
        val usdl = loadUSDL("test-data/order-usdl.json")
        val schema = serializer.generateSchema(usdl)

        // Write dummy Parquet file
        writeParquetFile("test-output/order.parquet", schema, sampleData)

        // Verify PySpark can read it
        val pysparkResult = runPySpark("""
            df = spark.read.parquet('test-output/order.parquet')
            print(df.schema)
        """)

        pysparkResult shouldContain "order_id"
    }
}
```

### 11.3 Integration Tests (5 tests)

- **CLI schema extraction test**
- **USDL → Parquet → Spark compatibility test**
- **Large file schema extraction test (100 MB+)**
- **Schema evolution test (add column)**
- **Multi-file partition schema consistency test**

### 11.4 Performance Tests (3 tests)

- **Schema extraction speed** (target: < 100ms for typical file)
- **Schema generation speed** (target: < 50ms)
- **Memory usage for large file schemas** (target: < 100 MB)

---

## 12. Dependencies & Libraries

### 12.1 Apache Parquet Libraries

**Primary Dependency: parquet-mr (Java implementation)**
```gradle
// formats/parquet/build.gradle.kts
dependencies {
    implementation("org.apache.parquet:parquet-column:1.13.1")
    implementation("org.apache.parquet:parquet-hadoop:1.13.1")
    implementation("org.apache.hadoop:hadoop-common:3.3.6") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    // For logical types
    implementation("org.apache.parquet:parquet-avro:1.13.1")

    // Testing
    testImplementation("org.apache.parquet:parquet-tools:1.13.1")
}
```

**Library Characteristics:**
- **Version:** 1.13.1 (stable, released 2024-01)
- **License:** Apache 2.0 (compatible with UTL-X AGPL/Commercial)
- **Size:** ~3 MB (parquet-column + parquet-hadoop)
- **Dependencies:** Hadoop Common (adds ~20 MB) - unavoidable for Parquet support
- **Maturity:** Very mature (10+ years, Apache Top-Level Project)

### 12.2 Optional: Apache Arrow (Phase 2)

**For Data Transformation (Phase 2):**
```gradle
dependencies {
    // Phase 2: For efficient columnar data handling
    implementation("org.apache.arrow:arrow-vector:14.0.1")
    implementation("org.apache.arrow:arrow-memory-netty:14.0.1")
}
```

**Arrow Benefits:**
- Efficient columnar in-memory representation
- Zero-copy data sharing
- Better performance for data transformation
- Inter-language compatibility (Java ↔ Python ↔ Rust)

### 12.3 Dependency Size Impact

| Library | Size | Required For | Phase |
|---------|------|--------------|-------|
| parquet-column | 1.5 MB | Schema parsing | 1 |
| parquet-hadoop | 1.2 MB | File I/O | 1 |
| hadoop-common | 20 MB | Parquet dependency | 1 |
| parquet-avro | 500 KB | Logical types | 1 |
| arrow-vector | 2 MB | Data transformation | 2 |
| arrow-memory | 500 KB | Memory management | 2 |

**Total Dependency Size:**
- **Phase 1 (Schema):** ~23 MB
- **Phase 2 (Data):** ~26 MB

**Mitigation for Size:**
- Use Gradle's dependency exclusions for unused Hadoop modules
- Consider ProGuard/R8 for CLI distribution
- Document dependency requirements clearly

---

## 13. Alternatives Considered

### Alternative 1: Generate Parquet Schema JSON (No Library)

**Approach:** Generate Parquet schema as JSON string without using Parquet libraries

**Pros:**
- No heavy dependencies (Hadoop, Parquet)
- Simple implementation
- Fast build times

**Cons:**
- ❌ JSON schema format is not standardized for Parquet
- ❌ Cannot validate schema correctness
- ❌ Cannot read actual Parquet files
- ❌ No compatibility guarantees with Spark/Hive

**Verdict:** ❌ **Rejected** - Too risky, no standard format

---

### Alternative 2: Use PyArrow via JNI

**Approach:** Call Python's PyArrow library from Java via JNI or process execution

**Pros:**
- PyArrow is very popular and well-documented
- Simpler API than parquet-mr
- Active development

**Cons:**
- ❌ Requires Python runtime (not self-contained)
- ❌ Complex JNI bridge or process spawning
- ❌ Performance overhead
- ❌ Deployment complexity

**Verdict:** ❌ **Rejected** - Adds too much complexity

---

### Alternative 3: Use Apache Arrow Flight for Data Access

**Approach:** Use Arrow Flight RPC protocol for Parquet data access

**Pros:**
- Modern, high-performance protocol
- Language-agnostic
- Streaming support

**Cons:**
- ❌ Requires running Flight server
- ❌ Overkill for local file access
- ❌ Adds network layer complexity

**Verdict:** ❌ **Rejected** - Not suitable for local file processing

---

### Alternative 4: Schema-Only via Text Parsing (Parquet Tools)

**Approach:** Use `parquet-tools` CLI to extract schema as text, parse output

**Pros:**
- No library dependencies (use existing tool)
- Simple implementation

**Cons:**
- ❌ Fragile (text parsing)
- ❌ Requires external tool installation
- ❌ No programmatic schema generation
- ❌ Cannot validate or manipulate schemas

**Verdict:** ❌ **Rejected** - Too fragile for production use

---

### **Selected Alternative: Use Official parquet-mr Library**

**Rationale:**
- ✅ Official Apache implementation
- ✅ Mature and well-tested
- ✅ Full feature support
- ✅ Guaranteed compatibility with Parquet ecosystem
- ✅ Schema validation included
- ✅ Can extend to data transformation (Phase 2)

**Trade-off:** Heavy dependencies (Hadoop), but unavoidable for proper Parquet support

---

## 14. Success Metrics

### 14.1 Technical Metrics

**Phase 1 (Schema Support):**
- ✅ 100% USDL directive coverage for Parquet (%repetition, %encoding, %compression, %logicalType)
- ✅ ≥ 90% test coverage (unit + integration tests)
- ✅ Schema extraction: < 100ms for typical file (< 100 MB)
- ✅ Schema generation: < 50ms from USDL
- ✅ Spark/Hive compatibility: 100% (all generated schemas readable)
- ✅ Parquet specification compliance: 100%

**Phase 2 (Data Transformation):**
- ✅ Read throughput: ≥ 50 MB/s (compressed Parquet → UDM)
- ✅ Write throughput: ≥ 30 MB/s (UDM → compressed Parquet)
- ✅ Memory usage: < 500 MB for 1 GB file processing
- ✅ Streaming support for large files (10+ GB)

### 14.2 User Adoption Metrics

**6 Months Post-Launch:**
- 10-20% of UTL-X transformations involve Parquet format
- 5+ community-contributed Parquet transformation examples
- 100+ schema extractions per week (telemetry, opt-in)

**12 Months Post-Launch:**
- 20-40% of Big Data transformations use UTL-X Parquet support
- Integration with 3+ data catalog tools (Glue, Collibra, Alation)
- 10+ enterprise customers using Parquet support in production

### 14.3 Business Metrics

**Value Proposition:**
- Reduce schema documentation effort by 70% (automated extraction)
- Enable Parquet ↔ XSD ↔ JSON Schema conversions (multi-format interoperability)
- Support data lake modernization projects (legacy → Parquet migration)

**Revenue Impact (Commercial Licensing):**
- 5+ commercial license sales attributed to Parquet support (Year 1)
- 20+ enterprise pilot projects using Parquet integration

### 14.4 Community Metrics

**Documentation & Evangelism:**
- 5+ blog posts about Parquet integration
- 3+ conference talks mentioning Parquet support
- Active forum discussions (>10 threads)
- 50+ GitHub stars on Parquet-related examples

---

## 15. References

### Apache Parquet Specification

- **Parquet Format Specification:** https://github.com/apache/parquet-format
- **Logical Type Annotations:** https://github.com/apache/parquet-format/blob/master/LogicalTypes.md
- **Parquet Encoding:** https://github.com/apache/parquet-format/blob/master/Encodings.md
- **Parquet Compression:** https://github.com/apache/parquet-format/blob/master/Compression.md

### Apache Parquet Libraries

- **parquet-mr (Java):** https://github.com/apache/parquet-mr
- **PyArrow (Python):** https://arrow.apache.org/docs/python/parquet.html
- **Apache Arrow:** https://arrow.apache.org/

### UTL-X Documentation

- **USDL 1.0 Specification:** [../language-guide/universal-schema-dsl.md](../language-guide/universal-schema-dsl.md)
- **Avro Integration Study:** [avro-integration-study.md](avro-integration-study.md)
- **UDM Documentation:** [../architecture/universal-data-model.md](../architecture/universal-data-model.md)

### Parquet Ecosystem

- **Spark Parquet:** https://spark.apache.org/docs/latest/sql-data-sources-parquet.html
- **Hive Parquet:** https://cwiki.apache.org/confluence/display/Hive/Parquet
- **AWS Glue:** https://docs.aws.amazon.com/glue/latest/dg/aws-glue-api-catalog-tables.html
- **Presto Parquet:** https://prestodb.io/docs/current/connector/hive.html#parquet-format

### Comparison Resources

- **Parquet vs Avro vs ORC:** https://www.datanami.com/2018/05/16/big-data-file-formats-demystified/
- **Choosing Parquet:** https://www.influxdata.com/blog/parquet-file-format/

---

## Appendix A: USDL Parquet Directives Summary

### USDL 1.0 Tier 3 Directives for Parquet

| Directive | Scope | Value Type | Description | Example |
|-----------|-------|------------|-------------|---------|
| `%logicalType` | Field | String | Semantic type annotation | `"date"`, `"timestamp-millis"`, `"decimal"`, `"uuid"`, `"json"` |
| `%repetition` | Field | String | Parquet repetition mode | `"required"`, `"optional"`, `"repeated"` |
| `%encoding` | Field | String | Parquet encoding type | `"plain"`, `"rle"`, `"delta"`, `"dictionary"` |
| `%compression` | Type | String | Compression algorithm | `"snappy"`, `"gzip"`, `"lzo"`, `"zstd"`, `"uncompressed"` |
| `%precision` | Field | Integer | Decimal precision | `10` (for DECIMAL(10,2)) |
| `%scale` | Field | Integer | Decimal scale | `2` (for DECIMAL(10,2)) |

### Usage Example

```json
{
  "%types": {
    "SalesRecord": {
      "%kind": "structure",
      "%compression": "zstd",
      "%fields": [
        {
          "%name": "sale_id",
          "%type": "integer",
          "%repetition": "required"
        },
        {
          "%name": "sale_date",
          "%type": "integer",
          "%logicalType": "date",
          "%repetition": "required"
        },
        {
          "%name": "amount",
          "%type": "number",
          "%logicalType": "decimal",
          "%precision": 12,
          "%scale": 2,
          "%repetition": "required"
        },
        {
          "%name": "notes",
          "%type": "string",
          "%repetition": "optional"
        },
        {
          "%name": "tags",
          "%type": "string",
          "%repetition": "repeated"
        },
        {
          "%name": "line_items",
          "%type": "structure",
          "%repetition": "repeated",
          "%fields": [
            {
              "%name": "product_id",
              "%type": "string",
              "%repetition": "required",
              "%encoding": "dictionary"
            },
            {
              "%name": "quantity",
              "%type": "integer",
              "%repetition": "required",
              "%encoding": "delta"
            }
          ]
        }
      ]
    }
  }
}
```

---

## Appendix B: Parquet Schema Object Structure

### MessageType Structure (from parquet-mr)

```kotlin
// Parquet schema representation
import org.apache.parquet.schema.*

// Build schema using Types builder API
val schema: MessageType = Types.buildMessage()
    .required(PrimitiveTypeName.INT64).named("order_id")
    .optional(PrimitiveTypeName.BYTE_ARRAY)
        .`as`(LogicalTypeAnnotation.stringType())
        .named("customer_name")
    .required(PrimitiveTypeName.INT32)
        .`as`(LogicalTypeAnnotation.dateType())
        .named("order_date")
    .required(PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY)
        .length(5)
        .`as`(LogicalTypeAnnotation.decimalType(2, 10))
        .named("total")
    .repeatedGroup()
        .required(PrimitiveTypeName.BYTE_ARRAY)
            .`as`(LogicalTypeAnnotation.stringType())
            .named("sku")
        .required(PrimitiveTypeName.INT32)
            .named("quantity")
        .named("items")
    .named("Order")

// Access schema elements
println(schema.name)  // "Order"
println(schema.fields.size)  // 5
println(schema.getType("order_id").repetition)  // REQUIRED
```

---

**END OF DOCUMENT**

---

## Document Metadata

**Version:** 1.0
**Status:** Draft - Ready for Review
**Approval Required From:** UTL-X Core Team, Project Lead
**Next Steps:** Review findings, decide on Phase 1 implementation priority
**Related Documents:**
- [Avro Integration Study](avro-integration-study.md)
- [USDL 1.0 Specification](../language-guide/universal-schema-dsl.md)
- [Format Module Architecture](../architecture/format-modules.md)

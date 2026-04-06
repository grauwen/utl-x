# Study Guide: CSV Metadata Definition in UTL-X 1.0
## Using %USDL (Universal Schema Definition Language)

**Author Context**: Marcel A. Grauwen (UTL-X Project)  
**Version**: 1.0 Draft  
**Date**: November 2025

---

## Executive Summary

**Context**: UTL-X 1.0 already has **%USDL (Universal Schema Definition Language)** working successfully for JSON Schema (`jsch`), XSD (XML Schema), and Apache Avro. USDL serves as a format-agnostic metadata definition layer that extends UTL-X's capabilities.

**Purpose**: This study guide evaluates existing CSV metadata standards to determine which best fits UTL-X's USDL framework for CSV support. After comprehensive analysis, we recommend **Frictionless Data's Table Schema** as the optimal choice for CSV metadata definition in UTL-X 1.0.

**Recommendation**: Use **`tsch`** (Table Schema) as the type identifier for Frictionless Table Schema, following the same pattern as `jsch` for JSON Schema.

**Why Frictionless Table Schema?**
1. **JSON-based**: Aligns perfectly with USDL's existing JSON Schema support
2. **Mature ecosystem**: Extensive tooling, active community, maintained by Open Knowledge Foundation
3. **Balanced approach**: Simpler than CSVW, more powerful than basic CSV Schema
4. **Format-agnostic design**: Part of broader Data Package spec, extensible to other formats
5. **Production-ready**: 9 programming language implementations with strong Python support

---

## Table of Contents

1. [Background: Metadata Standards](#1-background-metadata-standards)
2. [UTL-X Architecture Relevant to Metadata](#2-utl-x-architecture-relevant-to-metadata)
3. [Proposed %USDL Extension](#3-proposed-usdl-extension)
4. [CSV Metadata in %USDL](#4-csv-metadata-in-usdl)
5. [Integration Patterns](#5-integration-patterns)
6. [Implementation Roadmap](#6-implementation-roadmap)
7. [Examples and Use Cases](#7-examples-and-use-cases)
8. [Comparison with Existing Standards](#8-comparison-with-existing-standards)

---

## 0. USDL in UTL-X: Current State

### 0.1 Existing USDL Implementation

UTL-X 1.0 **already includes USDL** (Universal Schema Definition Language) with full support for:

**JSON Schema**
- Native JSON Schema validation
- Type definitions and constraints
- Reference resolution ($ref)
- Nested schema composition

**XSD (XML Schema Definition)**
- Complete XML Schema support
- Complex type definitions
- Element and attribute validation
- Namespace handling

**Apache Avro**
- Avro schema integration
- Logical types support
- Schema evolution compatibility
- Binary serialization metadata

### 0.2 USDL Architecture in UTL-X

USDL operates as a **schema abstraction layer** that:
1. Accepts format-specific schema definitions (JSON Schema, XSD, Avro)
2. Converts them to a unified internal representation
3. Provides validation and type inference to transformations
4. Integrates with the LSP daemon for IDE support

**Current Integration Points**:
- `modules/core/src/main/kotlin/org/apache/utlx/core/usdl/` - USDL core
- Format parsers reference USDL schemas for validation
- Type system leverages USDL for inference
- LSP daemon uses USDL for autocomplete and diagnostics

### 0.3 The CSV Challenge

CSV is the **only major format** in UTL-X without native USDL integration because:
1. CSV has no standard schema definition language (unlike JSON/XML/Avro)
2. Multiple competing CSV metadata standards exist
3. Different standards have different strengths and trade-offs

**This study guide evaluates CSV metadata standards to select the best fit for UTL-X's USDL framework.**

### 0.4 Naming Convention: `tsch` for Table Schema

Following UTL-X's established pattern of short, memorable type identifiers:
- **`jsch`** = JSON Schema
- **`xsd`** = XML Schema Definition  
- **`avro`** = Apache Avro Schema
- **`tsch`** = Table Schema (Frictionless) ⭐ **NEW**

The identifier `tsch` (pronounced "tee-sch" or "table-sch") will be used throughout UTL-X to represent Frictionless Table Schema metadata for CSV and other tabular formats.

---

## 1. Background: CSV Metadata Standards Evaluation

### 1.1 CSV Metadata Standards: Detailed Comparison

Three main standards exist for CSV metadata: CSV Schema, CSVW (W3C standard), and Frictionless Data's Table Schema. Below is a comprehensive comparison:

#### CSV Schema (csvschema.org)
**Description**: Lightweight JSON-based validation schema

**Strengths**:
- Very simple, minimal learning curve
- JSON-based (easy to read/write)
- Focused on validation

**Weaknesses**:
- Limited adoption, small community
- Basic type system only
- No relationship definitions
- Minimal tooling support
- Not actively maintained

**Verdict**: ❌ **Not Recommended** - Too limited for enterprise use

---

#### CSVW (CSV on the Web) - W3C Standard
**Description**: Rich metadata model using JSON-LD, XML Schema Datatypes, and Compact URIs

**Strengths**:
- Official W3C standard
- Comprehensive metadata model
- Semantic web integration (RDF, JSON-LD)
- Multi-table relationship support
- Conversion capabilities to/from Frictionless Data Package

**Weaknesses**:
- High complexity, steep learning curve
- Working group closed, limited ongoing development through community group without mandate for changes
- Limited software library support (4 languages: Java, Python, R, Ruby)
- Semantic web dependencies add overhead
- Overkill for most use cases

**Verdict**: ⚠️ **Not Recommended** - Too complex for UTL-X's design philosophy

---

#### Frictionless Data Table Schema ⭐ **RECOMMENDED**
**Description**: JSON-based schema specification for tabular data, part of the Data Package standard maintained by Open Knowledge Foundation

**Strengths**:
- **JSON-native**: Designed to be expressible in JSON with types based on JSON Schema
- **Active maintenance**: Published 2012, updated to v1.0 in 2017 and v2.0 in 2024, maintained by Open Knowledge Foundation with open governance
- **Strong adoption**: Libraries available for 9 programming languages (Go, Java, Javascript, Julia, PHP, Python, R, Ruby, Swift)
- **Comprehensive features**: 
  - Rich type system including string, number, integer, boolean, date, datetime, duration, geopoint, and more
  - Constraint system with required, minLength, maxLength, minimum, maximum, pattern, and enum
  - Foreign key relationships
  - Missing value handling
- **Extensible**: Designed to be extensible with custom properties via JSON Schema references
- **Ecosystem**: 
  - Open Data Editor for non-technical users
  - Integration with popular tools
  - Conversion to other standards (DataCite, DCAT)
- **Balance**: Simpler than CSVW, more powerful than CSV Schema

**Weaknesses**:
- Not a W3C standard (but actively governed by OKFN)
- Less semantic web integration than CSVW (but this is actually an advantage for simplicity)

**Verdict**: ✅ **STRONGLY RECOMMENDED** - Perfect fit for UTL-X

---

### 1.2 Why Frictionless Table Schema for USDL?

**Alignment with Existing USDL Stack**:
1. **JSON Schema Compatibility**: Types based on JSON Schema type set, making integration with USDL's JSON Schema support seamless
2. **Similar Abstraction Level**: Matches complexity of XSD and Avro schemas
3. **Format Agnostic Philosophy**: Table Schema is part of broader Data Package spec

**Technical Advantages**:
- Python reference implementation with 708 GitHub stars, extensive validation and streaming capabilities
- Works with UTL-X's Kotlin stack via JVM interop
- Bidirectional conversion with CSVW available if needed

**Community & Maintenance**:
- Open governance allowing pull requests from anyone, decided by working group from multiple organizations
- Regular updates (v2.0 in 2024)
- Production usage across many organizations

**Practical Benefits**:
- Field descriptors provide validation rules, additional information for reuse, and semantic annotations for interoperability
- Addresses CSV's lack of type information and relationship support
- Schema inference capabilities for bootstrapping

### 1.3 The `tsch` Type Identifier

**Rationale**: UTL-X uses short, memorable type identifiers for schema formats to maintain consistency and clarity in transformation code.

**Existing Pattern**:
| Format | Schema Type | Identifier | Pronunciation |
|--------|-------------|------------|---------------|
| JSON | JSON Schema | `jsch` | "jay-sch" |
| XML | XML Schema Definition | `xsd` | "ex-ess-dee" |
| Avro | Apache Avro Schema | `avro` | "ah-vro" |
| **CSV/Tabular** | **Table Schema** | **`tsch`** ⭐ | **"tee-sch"** or **"table-sch"** |

**Why `tsch` is the Right Choice**:

1. **Follows established convention**: Mirrors the pattern of `jsch` (4 characters, schema abbreviation)
2. **Official standard name**: "Table Schema" is Frictionless's official specification name
3. **Format agnostic**: Table Schema works for CSV, TSV, Excel - not limited to CSV only
4. **Clear and memorable**: Easy to type, pronounce, and recognize
5. **No collision**: Unique identifier with no conflicts in the ecosystem

**Usage Examples**:

Explicit type specification:
```utlx
schema customers.json type:tsch
```

Auto-detection (when USDL recognizes Table Schema structure):
```utlx
schema customers.json    // USDL auto-detects tsch format
```

In CLI commands:
```bash
utlx describe data.csv --output schema.json --format tsch
utlx validate --schema schema.json --type tsch --input data.csv
```

**Alternative Names Considered** (and why they were rejected):
- `csvsch` - Too CSV-specific, Table Schema works for all tabular formats
- `ftsch` - Unnecessarily verbose, "frictionless" is implied by standard
- `tabsch` - Less clear abbreviation, not official terminology
- `tableschema` - Too long, breaks established 4-5 character pattern

---

## 2. UTL-X Architecture Relevant to Metadata

### 2.1 Current UTL-X Header Directives

UTL-X 1.0 uses header directives for configuration:

```utlx
%utlx 1.0
input json
output xml
schema input.schema.json    // Current schema validation support
---
// Transformation logic
```

### 2.2 Universal Data Model (UDM)

UTL-X's core abstraction layer:
- **Location**: `modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt`
- **Purpose**: Format-agnostic representation of data
- **Key Types**: UdmValue, UdmObject, UdmArray, UdmScalar, UdmNull

All format parsers convert to UDM:
```
CSV + tsch → UDM ← JSON + jsch
               ↑
               ↓
             XML + xsd
               ↑
               ↓
            Avro + avro
```

### 2.3 Type System

UTL-X includes type inference and checking:
- **Location**: `modules/core/src/main/kotlin/org/apache/utlx/core/types/type_system.kt`
- **Current Capabilities**: Basic type checking for transformations
- **Enhancement Opportunity**: Schema-driven type inference

### 2.4 LSP Daemon Integration

The LSP daemon (Phase 2 Complete) provides:
- Type-aware autocomplete
- Hover information with type details
- Real-time diagnostics
- Document synchronization

**Metadata integration point**: USDL schemas could feed type information to the LSP daemon for enhanced IDE support.

---

## 3. Adding CSV Support to USDL via Frictionless Table Schema

### 3.1 Integration Strategy

Since USDL already exists and works with JSON Schema, XSD, and Avro, adding CSV support follows the established pattern:

**Existing Pattern**:
```
jsch (JSON Schema) → USDL Internal Representation → UTL-X Type System
xsd (XML Schema)   → USDL Internal Representation → UTL-X Type System
avro (Avro Schema) → USDL Internal Representation → UTL-X Type System
```

**New Addition**:
```
tsch (Table Schema) → USDL Internal Representation → UTL-X Type System
```

### 3.2 USDL Header Directive (Already Exists)

UTL-X already supports the schema directive. With `tsch` integration:

**Explicit type specification**:
```utlx
%utlx 1.0
input csv
output json
schema customers-schema.json type:tsch
---
// Transformation logic
```

**Auto-detection** (USDL detects Table Schema format from JSON structure):
```utlx
%utlx 1.0
input csv
output json
schema customers-schema.json
---
// Transformation logic
```

The schema file (`customers-schema.json`) contains a Frictionless Table Schema in standard JSON format.

---

## 4. Frictionless Table Schema for CSV in USDL

### 4.1 Table Schema Structure

Frictionless Table Schema uses JSON format that USDL will parse and convert to its internal representation:

```json
{
  "fields": [
    {
      "name": "customer_id",
      "type": "integer",
      "constraints": {
        "required": true,
        "unique": true,
        "minimum": 1
      }
    },
    {
      "name": "email",
      "type": "string",
      "format": "email",
      "constraints": {
        "required": true,
        "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
      }
    },
    {
      "name": "signup_date",
      "type": "date",
      "format": "default",
      "constraints": {
        "required": true
      }
    },
    {
      "name": "credit_score",
      "type": "integer",
      "constraints": {
        "minimum": 300,
        "maximum": 850
      }
    }
  ],
  "missingValues": ["", "N/A", "null"],
  "primaryKey": ["customer_id"]
}
```

### 4.2 CSV Dialect Description

For CSV parsing parameters, Table Schema integrates with **CSV Dialect Description Format**:

```json
{
  "dialect": {
    "delimiter": ",",
    "lineTerminator": "\r\n",
    "quoteChar": "\"",
    "doubleQuote": true,
    "skipInitialSpace": false,
    "header": true,
    "caseSensitiveHeader": false
  },
  "fields": [
    // Field definitions as above
  ]
}
```

### 4.3 Frictionless Type System

Table Schema types map directly to UTL-X UDM types:

| Table Schema Type | Format Options | UDM Type | Example Value |
|-------------------|----------------|----------|---------------|
| string | default, email, uri, binary, uuid | UdmScalar(string) | "John Doe" |
| number | default | UdmScalar(decimal) | 3.14159 |
| integer | default | UdmScalar(integer) | 42 |
| boolean | default | UdmScalar(boolean) | true |
| date | default, any, %Y-%m-%d | UdmScalar(date) | 2025-11-06 |
| time | default, any, %H:%M:%S | UdmScalar(time) | 14:30:00 |
| datetime | default, any, ISO8601 | UdmScalar(datetime) | 2025-11-06T14:30:00Z |
| year | default | UdmScalar(integer) | 2025 |
| yearmonth | default | UdmScalar(string) | 2025-11 |
| duration | default | UdmScalar(string) | P3Y6M4DT12H30M5S |
| geopoint | default, array, object | UdmObject | {"lon": -122.4, "lat": 37.8} |
| geojson | default, topojson | UdmObject | GeoJSON geometry |
| array | default | UdmArray | ["item1", "item2"] |
| object | default | UdmObject | {"key": "value"} |

### 4.4 Constraint System

Frictionless Table Schema constraints:

```json
{
  "fields": [
    {
      "name": "quantity",
      "type": "integer",
      "constraints": {
        "required": true,
        "minimum": 1,
        "maximum": 9999
      }
    },
    {
      "name": "email",
      "type": "string",
      "format": "email",
      "constraints": {
        "required": true,
        "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
      }
    },
    {
      "name": "status",
      "type": "string",
      "constraints": {
        "required": true,
        "enum": ["ACTIVE", "INACTIVE", "PENDING"]
      }
    },
    {
      "name": "description",
      "type": "string",
      "constraints": {
        "minLength": 10,
        "maxLength": 500
      }
    }
  ]
}
```

**Available Constraints**:
- `required`: Boolean, field must have a value
- `unique`: Boolean, all values must be unique
- `minimum`: Minimum numeric value
- `maximum`: Maximum numeric value
- `minLength`: Minimum string/array length
- `maxLength`: Maximum string/array length
- `pattern`: Regular expression for validation
- `enum`: Array of allowed values

### 4.5 Complete Sales Data Example (Frictionless Format)

```json
{
  "title": "Sales Transaction Records",
  "description": "Daily sales transactions from POS system",
  "fields": [
    {
      "name": "transaction_id",
      "type": "string",
      "description": "Unique transaction identifier",
      "constraints": {
        "required": true,
        "unique": true,
        "pattern": "^TXN[0-9]{10}$"
      }
    },
    {
      "name": "date",
      "type": "date",
      "format": "default",
      "description": "Transaction date",
      "constraints": {
        "required": true
      }
    },
    {
      "name": "customer_id",
      "type": "integer",
      "description": "Customer reference ID",
      "constraints": {
        "required": true,
        "minimum": 1000
      }
    },
    {
      "name": "product_code",
      "type": "string",
      "constraints": {
        "required": true,
        "pattern": "^[A-Z]{3}-[0-9]{4}$"
      }
    },
    {
      "name": "quantity",
      "type": "integer",
      "constraints": {
        "required": true,
        "minimum": 1,
        "maximum": 9999
      }
    },
    {
      "name": "unit_price",
      "type": "number",
      "constraints": {
        "required": true,
        "minimum": 0.01
      }
    },
    {
      "name": "discount_percent",
      "type": "number",
      "constraints": {
        "minimum": 0,
        "maximum": 100
      }
    },
    {
      "name": "total_amount",
      "type": "number",
      "constraints": {
        "required": true,
        "minimum": 0
      }
    },
    {
      "name": "payment_method",
      "type": "string",
      "constraints": {
        "required": true,
        "enum": ["CASH", "CREDIT_CARD", "DEBIT_CARD", "MOBILE_PAY"]
      }
    },
    {
      "name": "store_location",
      "type": "string",
      "constraints": {
        "required": true,
        "maxLength": 100
      }
    },
    {
      "name": "notes",
      "type": "string",
      "constraints": {
        "maxLength": 500
      }
    }
  ],
  "primaryKey": ["transaction_id"],
  "missingValues": ["", "N/A", "null"]
}
```

---

## 5. Integration Patterns

### 5.1 External Schema Files

**Use Case**: Reusable schema across multiple transformations

**Directory Structure**:
```
project/
├── schemas/
│   ├── customers-schema.json      # tsch (Table Schema)
│   ├── orders-schema.json         # tsch
│   └── products-schema.json       # tsch
├── transforms/
│   ├── customer_summary.utlx
│   ├── order_analysis.utlx
│   └── product_report.utlx
└── data/
    ├── customers.csv
    ├── orders.csv
    └── products.csv
```

**Schema File (customers-schema.json)** - `tsch` format:
```json
{
  "fields": [
    {
      "name": "customer_id",
      "type": "integer",
      "constraints": {"required": true, "unique": true}
    },
    {
      "name": "full_name",
      "type": "string",
      "constraints": {"required": true}
    },
    {
      "name": "email",
      "type": "string",
      "format": "email",
      "constraints": {"required": true}
    }
  ],
  "primaryKey": ["customer_id"]
}
```

**Transformation File**:
```utlx
%utlx 1.0
input csv
output json
schema ../schemas/customers-schema.json type:tsch
---
{
  customers: $input.* |> map(customer => {
    id: customer.customer_id,
    name: customer.full_name,
    email: customer.email
  })
}
```

**Or with auto-detection**:
```utlx
%utlx 1.0
input csv
output json
schema ../schemas/customers-schema.json    // Auto-detects as tsch
---
// Same transformation
```

### 5.2 Inline Schema Definition (Future Enhancement)

**Use Case**: Small, transformation-specific schemas

**Note**: While external schema files (`.json`) are the primary approach for `tsch`, future versions may support inline schema definitions for simple cases.

**Current approach** - external file:
```utlx
%utlx 1.0
input csv
output json
schema simple-schema.json type:tsch
---
{
  activeUsers: $input[active == true] |> map(user => {
    id: user.id,
    name: user.name
  })
}
```

Where `simple-schema.json` contains:
```json
{
  "fields": [
    {"name": "id", "type": "integer", "constraints": {"required": true}},
    {"name": "name", "type": "string", "constraints": {"required": true}},
    {"name": "active", "type": "boolean", "constraints": {"required": true}}
  ]
}
```

### 5.3 Multi-Input with Different Schemas

**Use Case**: Joining data from multiple sources with different schemas

```utlx
%utlx 1.0
input csv as customers from "customers.csv"
input csv as orders from "orders.csv"
output json
schema-customers ../schemas/customers-schema.json type:tsch
schema-orders ../schemas/orders-schema.json type:tsch
---
{
  customerOrders: $customers.* |> map(customer => {
    id: customer.customer_id,
    name: customer.name,
    orders: $orders[customer_id == customer.customer_id]
  })
}
```

**Alternative with auto-detection**:
```utlx
%utlx 1.0
input csv as customers from "customers.csv"
input csv as orders from "orders.csv"
output json
schema-customers ../schemas/customers-schema.json
schema-orders ../schemas/orders-schema.json
---
// Same transformation
```

### 5.4 Schema Generation

**Use Case**: Generating `tsch` schema from existing CSV

UTL-X CLI could support Table Schema generation:

```bash
# Generate tsch (Table Schema) from CSV file
utlx describe customers.csv --output customers-schema.json --format tsch

# With inference options
utlx describe customers.csv \
  --output customers-schema.json \
  --format tsch \
  --infer-types \
  --detect-patterns \
  --sample-size 10000
```

Generated schema (Frictionless Table Schema / `tsch` format):
```json
{
  "title": "Customers Dataset",
  "description": "Auto-generated schema from customers.csv",
  "$comment": "Generated: 2025-11-06T14:30:00Z, Sample size: 10000 rows",
  "fields": [
    {
      "name": "customer_id",
      "type": "integer",
      "description": "Inferred: No null values in sample",
      "constraints": {
        "required": true,
        "unique": true,
        "minimum": 1000,
        "maximum": 999999
      }
    },
    {
      "name": "email",
      "type": "string",
      "format": "email",
      "description": "Detected email pattern",
      "constraints": {
        "required": true,
        "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
      }
    },
    {
      "name": "signup_date",
      "type": "date",
      "format": "default",
      "constraints": {
        "required": true
      }
    }
  ],
  "primaryKey": ["customer_id"],
  "missingValues": ["", "N/A", "null"]
}
```

**Usage in transformation**:
```utlx
%utlx 1.0
input csv
output json
schema customers-schema.json type:tsch
---
// Transformation with validated data
```

### 5.5 Schema Validation Levels

UTL-X can support multiple validation levels:

```utlx
%utlx 1.0
%usdl 1.0
input csv
output json
schema customers.usdl
validate strict    // Options: none, lenient, strict, pedantic
---
// Transformation
```

**Validation Levels**:
- **none**: No validation, schema used only for type hints
- **lenient**: Warn on violations, continue processing
- **strict**: Error on violations, stop processing (default)
- **pedantic**: Strict + additional quality checks (encoding, formatting)

---

## 6. Implementation Roadmap

### Phase 1: Frictionless Parser Integration (Weeks 1-4)

**Goals**:
- Integrate Frictionless Table Schema parser into USDL
- Map Table Schema types to UDM types
- Support CSV Dialect Description Format

**Deliverables**:
- `modules/core/src/main/kotlin/org/apache/utlx/core/usdl/`
  - `frictionless_parser.kt` - Parse Table Schema JSON
  - `frictionless_mapper.kt` - Map to UDM types
  - `csv_dialect_handler.kt` - Handle dialect specifications
- Unit tests for Frictionless parsing
- Type mapping validation tests

**Key Dependencies**:
- Frictionless specifications v2.0
- Kotlin JSON parsing libraries
- Existing USDL infrastructure

### Phase 2: CSV Validation Integration (Weeks 5-8)

**Goals**:
- Enhance CSV format handler with schema validation
- Implement constraint checking
- Add missing value handling

**Deliverables**:
- Enhanced `formats/csv/src/main/kotlin/org/apache/utlx/formats/csv/`
  - Update CSV parser to use Table Schemas
  - Implement validation during parsing
  - Add constraint checking (required, min/max, pattern, enum)
  - Handle missing values per schema
- Conformance tests for CSV + Table Schema
- Error reporting for validation failures

### Phase 3: Schema Generation CLI (Weeks 9-10)

**Goals**:
- Add `utlx describe` command for schema generation
- Implement type inference from CSV data
- Support inference configuration options

**Deliverables**:
- `utlx describe csv` command
  ```bash
  utlx describe customers.csv --output customers-schema.json
  utlx describe customers.csv --infer-types --sample-size 10000
  ```
- Schema inference engine
  - Detect field types from data
  - Identify patterns (email, URLs, etc.)
  - Suggest constraints (min/max, enum)
  - Calculate statistics
- CLI documentation and examples

### Phase 4: LSP Daemon Enhancement (Weeks 11-12)

**Goals**:
- Feed Table Schema type info to LSP daemon
- Add schema-aware features to IDE integration
- Provide real-time validation

**Deliverables**:
- Enhanced LSP daemon (`modules/daemon/`)
  - Load Table Schemas for open files
  - Provide type information on hover
  - Autocomplete based on schema fields
  - Real-time validation diagnostics
- LSP conformance tests with Table Schema
- VSCode/IntelliJ plugin updates

### Phase 5: Documentation & Examples (Weeks 13-14)

**Goals**:
- Complete documentation for CSV + Table Schema
- Create tutorial examples
- Document migration from other standards

**Deliverables**:
- Tutorial: "Getting Started with CSV Schemas in UTL-X"
- Example repository with common use cases
- Migration guides:
  - From CSV Schema to Frictionless
  - From CSVW to Frictionless
  - From custom formats to Frictionless
- API documentation updates

### Phase 6: Ecosystem Integration (Weeks 15-16)

**Goals**:
- Integration with Frictionless tools
- Bidirectional conversion capabilities
- Community engagement

**Deliverables**:
- Frictionless Data Package support
  - Read/write Data Package descriptors
  - Multi-resource handling
  - Foreign key resolution
- Conversion tools:
  - Table Schema ↔ CSVW metadata
  - Table Schema ↔ JSON Schema
- Blog posts and community outreach
- Contribution to Frictionless ecosystem

---

## 7. Examples and Use Cases

### 7.1 Basic CSV Validation

**Scenario**: Validate customer data before processing

**customers.csv**:
```csv
customer_id,name,email,signup_date
1001,John Doe,john@example.com,2025-01-15
1002,Jane Smith,jane@example.com,2025-02-20
1003,Bob Wilson,invalid-email,2025-03-10
```

**customers-schema.json** (tsch format):
```json
{
  "fields": [
    {
      "name": "customer_id",
      "type": "integer",
      "constraints": {"required": true, "unique": true}
    },
    {
      "name": "name",
      "type": "string",
      "constraints": {"required": true}
    },
    {
      "name": "email",
      "type": "string",
      "format": "email",
      "constraints": {
        "required": true,
        "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
      }
    },
    {
      "name": "signup_date",
      "type": "date",
      "format": "default",
      "constraints": {"required": true}
    }
  ]
}
```

**Validation**:
```bash
utlx validate --schema customers-schema.json --input customers.csv --type tsch --level strict
```

**Output**:
```
Validation Errors (1):
  Line 4, Column 'email': Invalid email format
    Value: "invalid-email"
    Expected: Valid email address matching pattern
    Schema: customers-schema.json (tsch)
```

### 7.2 Type-Safe Transformation

**Scenario**: Transform CSV to JSON with type safety using `tsch`

**customers-schema.json** (tsch format - referenced by transformation):
```json
{
  "fields": [
    {"name": "customer_id", "type": "integer", "constraints": {"required": true}},
    {"name": "name", "type": "string", "constraints": {"required": true}},
    {"name": "email", "type": "string", "format": "email", "constraints": {"required": true}},
    {"name": "signup_date", "type": "date", "constraints": {"required": true}}
  ]
}
```

**transform.utlx**:
```utlx
%utlx 1.0
input csv
output json
schema customers-schema.json type:tsch
---
{
  customers: $input.* |> map(c => {
    id: c.customer_id,           // Type: Integer (from tsch schema)
    fullName: c.name,             // Type: String
    contact: {
      email: c.email,             // Type: Email (validated by tsch)
      verified: true
    },
    memberSince: c.signup_date,   // Type: Date (parsed correctly by tsch)
    daysActive: daysBetween(c.signup_date, now())
  })
}
```

**Benefits**:
- Type errors caught at parse time via `tsch` validation
- IDE provides type-aware autocomplete (via LSP + tsch)
- Automatic type conversions based on Table Schema
- Email format validation enforced by `tsch` constraints

### 7.3 Data Quality Report

**Scenario**: Generate data quality metrics using `tsch` validation

**quality_check.utlx**:
```utlx
%utlx 1.0
input csv
output json
schema sales-schema.json type:tsch
validate lenient    // Collect all violations from tsch validation
---
{
  summary: {
    totalRows: count($input.*),
    validRows: count($input[validationStatus == "valid"]),
    invalidRows: count($input[validationStatus == "invalid"])
  },
  violations: $input[validationStatus == "invalid"] |> map(row => {
    rowNumber: row._rowNumber,
    errors: row._validationErrors  // Errors from tsch constraints
  }),
  qualityScore: (count($input[validationStatus == "valid"]) / count($input.*)) * 100,
  schemaInfo: {
    type: "tsch",
    version: "1.0",
    fields: count($input._schema.fields)
  }
}
```

**Output example**:
```json
{
  "summary": {
    "totalRows": 1000,
    "validRows": 987,
    "invalidRows": 13
  },
  "violations": [
    {
      "rowNumber": 45,
      "errors": ["email: Invalid format (tsch constraint violation)"]
    },
    {
      "rowNumber": 127,
      "errors": ["price: Below minimum value of 0 (tsch constraint)"]
    }
  ],
  "qualityScore": 98.7,
  "schemaInfo": {
    "type": "tsch",
    "version": "1.0",
    "fields": 8
  }
}
```

### 7.4 Schema Evolution

**Scenario**: Handle schema changes over time

**Version 1**:
```usdl
schema ProductsV1 {
  format: csv
  version: "1.0"
  columns [
    { name: "product_id", type: Integer, required: true },
    { name: "name", type: String, required: true },
    { name: "price", type: Decimal, required: true }
  ]
}
```

**Version 2** (added category):
```usdl
schema ProductsV2 {
  format: csv
  version: "2.0"
  
  migration {
    from: "1.0"
    changes: [
      { type: "add_column", column: "category", default: "UNCATEGORIZED" }
    ]
  }
  
  columns [
    { name: "product_id", type: Integer, required: true },
    { name: "name", type: String, required: true },
    { name: "price", type: Decimal, required: true },
    { 
      name: "category", 
      type: Enum, 
      required: true,
      default: "UNCATEGORIZED",
      constraints: [
        { enum: ["ELECTRONICS", "CLOTHING", "FOOD", "UNCATEGORIZED"] }
      ]
    }
  ]
}
```

**Transformation supporting both versions**:
```utlx
%utlx 1.0
%usdl 1.0
input csv
output json
schema products.usdl version-compatible    // Accept v1.0 or v2.0
---
{
  products: $input.* |> map(p => {
    id: p.product_id,
    name: p.name,
    price: p.price,
    category: p.category ?? "UNKNOWN"    // Handle missing column in v1
  })
}
```

### 7.5 Cross-Format Consistency

**Scenario**: Ensure consistency between CSV and JSON versions

**Both formats share schema concepts**:

```usdl
// customer_schema.usdl
schema Customer {
  // Format-agnostic field definitions
  fields [
    { name: "id", type: Integer, required: true, unique: true },
    { name: "name", type: String, required: true },
    { name: "email", type: Email, required: true },
    { name: "active", type: Boolean, required: true }
  ]
  
  // Format-specific mappings
  formats {
    csv: {
      structure: {
        delimiter: ",",
        header: true
      },
      column_order: ["id", "name", "email", "active"]
    },
    json: {
      root: "customer",
      array_wrapper: false
    },
    xml: {
      root: "customer",
      attributes: ["id"],
      elements: ["name", "email", "active"]
    }
  }
}
```

**Usage**:
```utlx
%utlx 1.0
%usdl 1.0
input csv
output json
schema customer_schema.usdl
---
// Schema ensures both formats have same fields and types
$input
```

---

## 8. Final Recommendation: Frictionless Table Schema

### 8.1 Decision Matrix

| Criteria | CSV Schema | CSVW | **Frictionless** ⭐ | Weight |
|----------|------------|------|---------------------|--------|
| **JSON Schema Compatibility** | Partial | Complex | ✅ Native | High |
| **Existing USDL Alignment** | Poor | Moderate | ✅ Excellent | Critical |
| **Type System Richness** | Basic | Comprehensive | ✅ Comprehensive | High |
| **Community & Maintenance** | Weak | Stagnant | ✅ Active | High |
| **Learning Curve** | Easy | Steep | ✅ Moderate | Medium |
| **Tool Ecosystem** | Minimal | Limited (4 langs) | ✅ Extensive (9 langs) | High |
| **Extensibility** | Limited | Complex | ✅ Good | Medium |
| **Performance** | N/A | N/A | ✅ Proven | Medium |
| **Standards Body** | None | W3C (closed) | OKFN (active) | Low |

**Overall Score**: Frictionless wins decisively across all critical criteria.

### 8.2 Why Frictionless is the Best Fit

**Technical Alignment**:
1. **JSON-native design** maps perfectly to USDL's JSON Schema support
2. **Type system based on JSON Schema** enables seamless USDL integration
3. **Balanced complexity** - simpler than CSVW, more powerful than CSV Schema
4. **Already proven** in production environments across many organizations

**Ecosystem Benefits**:
- Python reference implementation (708★) works well with Kotlin/JVM via interop
- 9 programming language implementations available
- Frictionless Framework provides validation, extraction, and transformation
- Can convert to/from CSVW if needed for interoperability

**Community & Governance**:
- Active maintenance by Open Knowledge Foundation
- Open governance model (pull requests welcome)
- Regular updates (v2.0 released 2024)
- Large user base providing feedback and extensions

**Future-Proof**:
- Part of broader Data Package specification
- Extensible via JSON Schema references
- Conversion tools to other standards (DataCite, DCAT)
- Semantic annotations possible via custom properties

### 8.3 Integration Path for UTL-X

**Phase 1**: USDL Parser Extension
- Add Frictionless Table Schema parser to USDL
- Map Table Schema types to UDM types
- Handle CSV Dialect Description Format

**Phase 2**: CSV Format Handler Integration  
- Update CSV parser to use Table Schema for validation
- Implement constraint checking during parsing
- Add missing value handling

**Phase 3**: Tooling & CLI
- `utlx describe` command to generate Table Schemas from CSV
- Schema inference with configurable options
- Validation reporting

**Phase 4**: LSP Daemon Enhancement
- Feed Table Schema type info to autocomplete
- Real-time validation diagnostics
- Schema-aware hover information

---

## 9. Open Questions and Design Decisions

### 9.1 Questions to Resolve

1. **Syntax Choice**: Should USDL use:
   - JSON-like syntax (familiar, but verbose)
   - Custom DSL (cleaner, but new learning curve)
   - YAML-inspired (readable, but whitespace-sensitive)
   - **Recommendation**: Custom DSL similar to UTL-X transformation syntax

2. **Schema Versioning**: How to handle schema evolution?
   - Explicit version numbers
   - Compatibility declarations
   - Migration specifications
   - **Recommendation**: All three, with clear migration paths

3. **Validation Timing**: When to validate?
   - At parse time (catch errors early)
   - At transformation time (flexible)
   - Both (best practice)
   - **Recommendation**: Both, controlled by validation level

4. **Performance**: Schema validation impact?
   - Caching compiled schemas
   - Lazy validation
   - Parallel validation
   - **Recommendation**: All three strategies

5. **Extensibility**: How to support custom types/validators?
   - Plugin system
   - User-defined functions in USDL
   - External validator references
   - **Recommendation**: Plugin system + UDF support

### 9.2 Design Trade-offs

**Simplicity vs. Power**:
- Simple schemas should be trivial to write
- Complex scenarios should be possible
- **Resolution**: Sensible defaults, progressive disclosure

**Independence vs. Integration**:
- USDL should work independently
- Deep UTL-X integration provides value
- **Resolution**: .usdl files standalone, enhanced when used with UTL-X

**Performance vs. Features**:
- Rich validation can be expensive
- Transformations must remain fast
- **Resolution**: Optional validation levels, compiled schema caching

---

## 10. Next Steps and Recommendations

### 10.1 Immediate Actions

1. **Finalize USDL Specification**
   - Review this study guide with stakeholders
   - Define formal grammar
   - Create specification document

2. **Build Prototype**
   - Implement basic USDL parser
   - Add CSV integration
   - Create sample schemas

3. **Gather Feedback**
   - Share with UTL-X community
   - Collect use cases
   - Iterate on design

### 10.2 Long-term Goals

1. **USDL 1.0 Release**
   - Complete specification
   - Full CSV support
   - Comprehensive testing

2. **Multi-format Support**
   - Extend to JSON, XML, YAML
   - Ensure format agnostic principles

3. **Ecosystem Growth**
   - Schema repository/marketplace
   - Schema generation tools
   - IDE plugins and extensions

4. **Standards Alignment**
   - Consider W3C CSVW compatibility layer
   - JSON Schema integration
   - XML Schema (XSD) bridging

---

## 11. Conclusion

**Decision**: UTL-X should adopt **Frictionless Data's Table Schema** as the CSV metadata standard within its existing USDL framework, using the type identifier **`tsch`** (Table Schema).

### Key Findings

1. **USDL Already Exists**: UTL-X already has a working USDL implementation for `jsch` (JSON Schema), `xsd` (XML Schema), and `avro` (Apache Avro)

2. **CSV Needs Standardization**: CSV is the only major format lacking native USDL support due to absence of a universal schema standard

3. **Frictionless is the Clear Winner**: After evaluating CSV Schema, CSVW, and Frictionless Table Schema, Frictionless emerged as the best choice for:
   - JSON-native design aligning with USDL's JSON Schema support
   - Comprehensive type system and constraints
   - Active maintenance and extensive tooling (9 programming languages)
   - Balanced complexity - more powerful than CSV Schema, simpler than CSVW
   - Production-ready with large community adoption

4. **`tsch` as Type Identifier**: Following UTL-X's naming convention (`jsch`, `xsd`, `avro`), we designate **`tsch`** as the identifier for Table Schema, pronounced "tee-sch" or "table-sch"

### Implementation Benefits

**Immediate Value**:
- Leverage existing Frictionless ecosystem and tools
- Use battle-tested schemas from the community
- Benefit from ongoing development by Open Knowledge Foundation
- Clear, memorable type identifier that follows established patterns

**Long-term Advantages**:
- Consistent metadata approach across all UTL-X formats
- Enhanced IDE support via LSP daemon with `tsch` type information
- Improved data quality through validation
- Better interoperability with data science ecosystem

**Migration Path**:
- Clear 16-week implementation roadmap
- Backward compatible with existing UTL-X transformations
- Conversion tools for other standards (CSVW, CSV Schema)

### Next Steps

1. **Approve Frictionless Table Schema (`tsch`)** as UTL-X's CSV metadata standard
2. **Begin Phase 1 implementation** (tsch parser integration into USDL)
3. **Engage with Frictionless community** on UTL-X integration
4. **Document `tsch` usage patterns** for early adopters
5. **Create example repository** showcasing CSV schema capabilities with `tsch`

**The adoption of Frictionless Table Schema (`tsch`) will complete UTL-X's format-agnostic vision, providing a unified, powerful metadata system across all major data formats: CSV (`tsch`), JSON (`jsch`), XML (`xsd`), YAML, Avro (`avro`), and Protobuf.**

---

## Appendix A: Frictionless Table Schema Quick Reference

### Minimal Schema
```json
{
  "fields": [
    {"name": "id", "type": "integer"},
    {"name": "name", "type": "string"}
  ]
}
```

### Complete Schema Template
```json
{
  "title": "Dataset Title",
  "description": "Dataset description",
  "fields": [
    {
      "name": "field_name",
      "type": "string|number|integer|boolean|date|time|datetime|year|yearmonth|duration|geopoint|geojson|array|object",
      "format": "default|email|uri|binary|uuid|(date patterns)",
      "title": "Human-readable title",
      "description": "Field description",
      "example": "Example value",
      "constraints": {
        "required": true,
        "unique": false,
        "minimum": 0,
        "maximum": 100,
        "minLength": 1,
        "maxLength": 255,
        "pattern": "regex pattern",
        "enum": ["value1", "value2"]
      }
    }
  ],
  "missingValues": ["", "N/A", "null"],
  "primaryKey": ["field1"],
  "foreignKeys": [
    {
      "fields": ["field1"],
      "reference": {
        "resource": "other_resource",
        "fields": ["id"]
      }
    }
  ]
}
```

### Type Reference

| Type | Description | Example |
|------|-------------|---------|
| string | Text | "Hello" |
| number | Decimal | 3.14 |
| integer | Whole number | 42 |
| boolean | True/False | true |
| date | ISO date | "2025-11-06" |
| time | ISO time | "14:30:00" |
| datetime | ISO datetime | "2025-11-06T14:30:00Z" |
| year | Year | 2025 |
| yearmonth | Year-month | "2025-11" |
| duration | ISO 8601 duration | "P3Y6M4DT12H30M5S" |
| geopoint | Geographic point | "lon, lat" or [lon, lat] |
| geojson | GeoJSON geometry | {...} |
| array | Array of values | [...] |
| object | JSON object | {...} |

### Constraint Reference

| Constraint | Types | Description |
|------------|-------|-------------|
| required | all | Field must have value |
| unique | all | Values must be unique |
| minimum | number, integer | Minimum value |
| maximum | number, integer | Maximum value |
| minLength | string, array | Minimum length |
| maxLength | string, array | Maximum length |
| pattern | string | Regex pattern |
| enum | all | Allowed values |

### CSV Dialect (Optional)
```json
{
  "dialect": {
    "delimiter": ",",
    "lineTerminator": "\r\n",
    "quoteChar": "\"",
    "doubleQuote": true,
    "skipInitialSpace": false,
    "header": true,
    "caseSensitiveHeader": false,
    "csvddfVersion": "1.2"
  }
}
```

---

## Appendix B: Resources and References

### Frictionless Data (Recommended)
- **Table Schema Specification**: https://specs.frictionlessdata.io/table-schema/
- **Data Package Specification**: https://specs.frictionlessdata.io/data-package/
- **CSV Dialect Description**: https://specs.frictionlessdata.io/csv-dialect/
- **Frictionless Framework (Python)**: https://framework.frictionlessdata.io/
- **GitHub Repository**: https://github.com/frictionlessdata
- **Open Data Editor**: Tool for creating Data Packages

### Other CSV Metadata Standards (For Reference)
- **CSV Schema**: https://csvschema.org/
- **CSVW (W3C)**: https://www.w3.org/TR/tabular-data-primer/
- **CSV Lint**: https://csvlint.io/ (CSVW validation tool)

### UTL-X Project
- **GitHub**: http://github.com/grauwen/utl-x
- **Documentation**: See CLAUDE.md in repository
- **Project Lead**: Ir. Marcel A. Grauwen

### Related Technologies
- **Apache Avro**: https://avro.apache.org/
- **JSON Schema**: https://json-schema.org/
- **XML Schema (XSD)**: https://www.w3.org/XML/Schema

### Academic & Standards References
- **RFC 4180** (CSV Format): https://tools.ietf.org/html/rfc4180
- **JSON-LD**: https://json-ld.org/
- **Dublin Core**: https://www.dublincore.org/
- **DCAT (Data Catalog Vocabulary)**: https://www.w3.org/TR/vocab-dcat/

---

**Document Version**: 1.0  
**Last Updated**: November 6, 2025  
**Author**: Study guide based on UTL-X project by Marcel A. Grauwen  
**Status**: Draft for discussion and feedback

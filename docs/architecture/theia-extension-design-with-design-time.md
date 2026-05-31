# UTL-X Theia Extension - IDE Architecture (Modes, Lifecycle, Packaging)

**Version:** 3.0
**Date:** 2026-05-31
**Status:** Implemented (modes, Phase 1-2) + Roadmap (lifecycle/watchdog, Electron, installers)
**Authors:** UTL-X Architecture Team

> v3.0 extends the dual-mode IDE design with the full *product* picture: process
> lifecycle & supervision, the die-with-parent watchdog, daemon runtime choices
> (JVM jar vs. bundled JRE vs. a future native build), the Electron desktop shell,
> and per-platform signed installers that embed `utlxd` for a no-prerequisites IDE.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Message Contract vs Execution Distinction](#message-contract-vs-execution-distinction)
3. [Architecture Overview](#architecture-overview)
4. [Message Contract Mode](#message-contract-mode)
5. [Execution Mode](#execution-mode)
6. [Mode Switching & UI](#mode-switching--ui)
7. [Three-Panel Layout (Enhanced)](#three-panel-layout-enhanced)
8. [LSP/Daemon Integration](#lspdaemon-integration)
9. [Workflow Examples](#workflow-examples)
10. [Implementation Phases](#implementation-phases)
11. [Process Lifecycle & Service Supervision](#process-lifecycle--service-supervision)
12. [Process Watchdog: Die-With-Parent](#process-watchdog-die-with-parent)
13. [Daemon Runtime: JVM Jar, Bundled JRE, and the Native Option](#daemon-runtime-jvm-jar-bundled-jre-and-the-native-option)
14. [Electron Desktop Application](#electron-desktop-application)
15. [Per-Platform Installers (macOS, Windows, Linux)](#per-platform-installers-macos-windows-linux)
16. [Bundle-Level IDE: Editing & Testing a `.utlar` Project](#bundle-level-ide-editing--testing-a-utlar-project)

---

## Executive Summary

### What Changed from v1.0?

The original Theia extension design (v1.0) focused only on **runtime execution**:
- Left panel: Input instance data (XML, JSON)
- Middle panel: UTL-X transformation
- Right panel: Output instance data (JSON, XML)

This design is enhanced with **Message Contract Mode** where:
- Left panel: Input **schema/metadata** (XSD, JSON Schema)
- Middle panel: UTL-X transformation (with type checking)
- Right panel: Output **schema/metadata** (inferred JSON Schema)

### Key Benefits

**Message Contract Mode:**
- ✅ Catch type errors before deployment
- ✅ Validate API contracts
- ✅ Autocomplete based on schema structure
- ✅ Generate output schema for downstream consumers
- ✅ No need for representative test data

**Execution Mode:**
- ✅ Test with actual data instances
- ✅ Debug transformation logic
- ✅ Verify output format
- ✅ Performance testing

---

## Message Contract vs Execution Distinction

### CRITICAL: Tier-1 vs Tier-2 Formats

**Message Contract Mode ONLY applies to Tier-1 data format transformations:**
- **Tier-1 Formats** (instance data): XML, JSON, YAML, CSV
- **Tier-2 Formats** (metadata/schemas): XSD, JSON Schema, Avro Schema (.avsc), Protobuf (.proto)

**The mode depends on what the UTLX declares as `input`/`output`:**

| UTLX Declaration | Transformation Type | Mode |
|------------------|---------------------|------|
| `input xml`<br>`output json` | Data-to-data transformation | Message Contract or Execution |
| `input xsd`<br>`output jsch` | Schema-to-schema transformation | Execution only (metadata IS the data) |

### Three Distinct Scenarios

#### Scenario 1: MESSAGE CONTRACT Mode (Type-Checking for Tier-1)

```
┌─────────────────────────────────────────────────────────────────┐
│ MESSAGE CONTRACT: Schema/Metadata Analysis for Data Transformations │
│                                                                 │
│  UTLX Declaration:  input xml / output json                    │
│                                                                 │
│  Left Panel:           Middle Panel:        Right Panel:       │
│  XSD (for validation)  UTL-X Transform     JSON Schema         │
│                        (type-checked)       (inferred)         │
│  <xs:element           %utlx 1.0            {                  │
│    name="Order"        input xml  ←tier-1   "$schema": "..."  │
│    type="OrderType"/>  output json←tier-1   "properties": {   │
│                        ---                    "id": {         │
│  <xs:complexType       {                       "type":        │
│    name="OrderType">     id: $input            "string"      │
│    <xs:attribute           .Order.@id,       }               │
│      name="id"           ...                 }                │
│      type="xs:string"  }                   }                  │
│    />                                                         │
│  </xs:complexType>                                            │
│                                                               │
│  Purpose: Type-check the transformation against schemas       │
│  XSD is METADATA about the XML data, not the data itself     │
│  Type Check: $input.Order.@id returns String ✓               │
└───────────────────────────────────────────────────────────────┘
```

#### Scenario 2: EXECUTION Mode (Data Transformation)

```
┌─────────────────────────────────────────────────────────────────┐
│ EXECUTION: Instance Data Execution                               │
│                                                                 │
│  UTLX Declaration:  input xml / output json                    │
│                                                                 │
│  Left Panel:           Middle Panel:        Right Panel:       │
│  XML Data (instance)   UTL-X Transform     JSON Data (result)  │
│                                                                 │
│  <Order id="ORD-001">  %utlx 1.0            {                  │
│    <Customer>          input xml  ←tier-1     "id":           │
│      <Name>John</Name> output json←tier-1     "ORD-001",      │
│    </Customer>         ---                    "customer":     │
│    <Items>             {                      "John",         │
│      <Item price="10"    id: $input          ...              │
│            qty="2"/>         .Order.@id,   }                  │
│    </Items>              ...                                  │
│  </Order>              }                                       │
│                                                                │
│  Purpose: Execute transformation on actual data               │
│  Execute: Transform actual data values                        │
└───────────────────────────────────────────────────────────────┘
```

#### Scenario 3: METADATA-TO-METADATA Transformation (Always Execution!)

```
┌─────────────────────────────────────────────────────────────────┐
│ METADATA TRANSFORMATION: Schema Engineering (NOT Message Contract!) │
│                                                                 │
│  UTLX Declaration:  input xsd / output jsch                    │
│                                                                 │
│  Left Panel:           Middle Panel:        Right Panel:       │
│  XSD File (the DATA)   UTL-X Transform     JSON Schema (OUTPUT)│
│                                                                 │
│  <?xml version="1.0">  %utlx 1.0            {                  │
│  <xs:schema>           input xsd  ←tier-2   "$schema": "..."  │
│    <xs:element         output jsch←tier-2   "type": "object"  │
│      name="Order"      ---                  "properties": {   │
│      type="string"/>   {                      "Order": {      │
│  </xs:schema>            type: "object",       "type":        │
│                          properties:           "string"       │
│                            $input            }                │
│                            .schema           }                 │
│                            .elements       }                   │
│                            |> map(...)                        │
│                        }                                       │
│                                                                │
│  Purpose: Convert one schema format to another                │
│  This is EXECUTION mode - the schema IS the data being          │
│  transformed, not metadata for validation!                    │
│  No "meta-meta-data" exists for type checking                 │
└───────────────────────────────────────────────────────────────┘
```

### When to Use Each Mode/Scenario

| Use Message Contract When... | Use Execution When... | Use Metadata-to-Metadata When... |
|-------------------------|---------------------|----------------------------------|
| **UTLX**: `input xml/json/yaml/csv`<br>`output json/xml/yaml/csv` | **UTLX**: `input xml/json/yaml/csv`<br>`output json/xml/yaml/csv` | **UTLX**: `input xsd/jsch/avsc/proto`<br>`output jsch/xsd/avsc/proto` |
| Building a new transformation | Testing with real data | Converting schema formats |
| Validating API contracts | Debugging transformation logic | Generating schemas from schemas |
| Generating documentation | Performance testing | Schema engineering tasks |
| No test data available yet | Verifying edge cases | Building schema converters |
| Early in development cycle | Late in development/production | Schema migration projects |
| Schema-driven development | Instance-driven development | Schema evolution/versioning |
| **Requires**: Tier-1 formats only | **Allows**: Any format (tier-1 or tier-2) | **Requires**: Tier-2 formats as data |

---

## Architecture Overview

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    Theia Frontend (Browser)                      │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Mode Selector: [Message Contract] [Execution]                   │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─────────────┬────────────────────┬────────────────────────┐  │
│  │    Left     │      Middle        │        Right           │  │
│  │    Panel    │   (UTL-X Editor)   │        Panel           │  │
│  │             │    with LSP        │                        │  │
│  │             │                    │                        │  │
│  │  CONTRACT:  │                    │  CONTRACT:             │  │
│  │  Schema/    │  %utlx 1.0        │  Output                │  │
│  │  Metadata   │  input xml        │  Schema                │  │
│  │  (XSD,      │  output json      │  (JSON Schema)         │  │
│  │  JSON       │  ---              │                        │  │
│  │  Schema)    │  {                │  + Type errors         │  │
│  │             │    id: $input...  │  + Validation          │  │
│  │  EXECUTION:   │  }                │                        │  │
│  │  Instance   │                   │  EXECUTION:              │  │
│  │  Data       │                   │  Output                │  │
│  │  (XML,JSON) │                   │  Data                  │  │
│  └─────────────┴────────────────────┴────────────────────────┘  │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Status Bar:                                               │  │
│  │  Mode: Message Contract | Schema: orders.xsd | Type Check: ✓   │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
                              ↕ JSON-RPC (LSP)
┌──────────────────────────────────────────────────────────────────┐
│                   UTL-X Daemon (Background)                      │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Mode-Aware State Manager                                  ││
│  │                                                             ││
│  │  Message Contract State:                Execution State:          ││
│  │  - Input schema (XSD)          - Input data instances      ││
│  │  - Type environment            - Execution context         ││
│  │  - Inferred output schema      - Output data               ││
│  │  - Type errors/warnings        - Execution errors            ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Analysis Engine                                            ││
│  │  ┌──────────────────┐  ┌──────────────────┐                ││
│  │  │ Message Contract:     │  │ Execution:         │                ││
│  │  │ - Schema Parser  │  │ - Data Parser    │                ││
│  │  │ - Type Inference │  │ - Transformer    │                ││
│  │  │ - Type Checker   │  │ - Serializer     │                ││
│  │  └──────────────────┘  └──────────────────┘                ││
│  └─────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

---

## Message Contract Mode

### Purpose

Analyze transformations using **schema metadata** instead of instance data.

**IMPORTANT CONSTRAINT**: Message Contract mode only works when the UTLX transformation declares **tier-1 formats**:
- ✅ `input xml` / `output json` - Message Contract supported
- ✅ `input json` / `output xml` - Message Contract supported
- ✅ `input yaml` / `output json` - Message Contract supported
- ✅ `input csv` / `output json` - Message Contract supported
- ❌ `input xsd` / `output jsch` - NOT Message Contract mode (this is metadata-to-metadata, see separate section)
- ❌ `input avsc` / `output proto` - NOT Message Contract mode (this is metadata-to-metadata)

### Left Panel: Input Schema (Metadata for Validation)

**Displays schemas that describe the structure of tier-1 input data:**
- XSD (XML Schema Definition) - describes XML structure
- JSON Schema - describes JSON structure
- YAML Schema - describes YAML structure
- CSV Schema - describes CSV structure

**These schemas are METADATA about the data, not the data itself being transformed.**

**UI Components:**
```typescript
interface InputSchemaPanel {
    // Schema content editor
    schemaEditor: MonacoEditor;

    // Schema format selector
    schemaFormat: 'xsd' | 'json-schema' | 'yaml-schema' | 'proto';

    // Schema validation status
    schemaValid: boolean;
    schemaErrors: Diagnostic[];

    // Actions
    loadSchemaFromFile(): Promise<void>;
    loadSchemaFromWorkspace(): Promise<void>;
    generateSampleInstance(): Promise<void>; // Generate example data

    // Schema tree view (optional)
    treeView?: SchemaTreeView;
}
```

**Example XSD:**
```xml
<?xml version="1.0"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="Order" type="OrderType"/>

  <xs:complexType name="OrderType">
    <xs:sequence>
      <xs:element name="Customer" type="CustomerType"/>
      <xs:element name="Items" type="ItemsType"/>
    </xs:sequence>
    <xs:attribute name="id" type="xs:string" use="required"/>
  </xs:complexType>

  <xs:complexType name="CustomerType">
    <xs:sequence>
      <xs:element name="Name" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="ItemsType">
    <xs:sequence>
      <xs:element name="Item" type="ItemType" maxOccurs="unbounded"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="ItemType">
    <xs:attribute name="price" type="xs:decimal"/>
    <xs:attribute name="quantity" type="xs:integer"/>
  </xs:complexType>
</xs:schema>
```

### Middle Panel: UTL-X with Type Checking

**Enhanced Features (via LSP):**
- **Type-aware autocomplete**: Knows `$input.Order.Customer.Name` is valid
- **Path validation**: Red underline if path doesn't exist in schema
- **Type mismatch errors**: e.g., `sum($input.Order.@id)` → Error: "Cannot sum strings"
- **Hover hints**: Shows type info (e.g., `$input.Order.@id: String`)

**LSP Messages (Message Contract):**
```json
// textDocument/didOpen with schema context
{
  "method": "textDocument/didOpen",
  "params": {
    "textDocument": {...},
    "utlxContext": {
      "mode": "design-time",
      "inputSchema": {
        "format": "xsd",
        "uri": "file:///schemas/order.xsd",
        "content": "<?xml version..."
      }
    }
  }
}

// Autocomplete request with schema-aware suggestions
{
  "method": "textDocument/completion",
  "params": {
    "position": {"line": 5, "character": 15},
    "context": {"mode": "design-time"}
  }
}

// Response: Schema-derived completions
{
  "result": [
    {"label": "Order", "kind": "Property", "type": "ObjectType"},
    {"label": "@id", "kind": "Attribute", "type": "String"},
    {"label": "Customer", "kind": "Property", "type": "ObjectType"},
    {"label": "Items", "kind": "Property", "type": "ObjectType"}
  ]
}
```

### Right Panel: Output Schema

**Displays:**
- Inferred JSON Schema from transformation
- Type errors and warnings
- Schema validation status

**UI Components:**
```typescript
interface OutputSchemaPanel {
    // Generated schema display
    schemaViewer: MonacoEditor; // Read-only JSON Schema

    // Type checking results
    typeCheckStatus: 'valid' | 'warnings' | 'errors';
    diagnostics: Diagnostic[];

    // Schema tree view
    treeView?: SchemaTreeView;

    // Actions
    exportSchema(): Promise<void>;
    compareWithExpected(): Promise<void>; // Compare with expected schema
    generateDocumentation(): Promise<void>; // Generate API docs
}
```

**Example Output Schema:**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": "Order ID from input.Order.@id"
    },
    "customerName": {
      "type": "string",
      "description": "Customer name from input.Order.Customer.Name"
    },
    "totalItems": {
      "type": "integer",
      "description": "Count of items array"
    },
    "totalValue": {
      "type": "number",
      "description": "Sum of (price * quantity)"
    }
  },
  "required": ["id", "customerName", "totalItems", "totalValue"]
}
```

### Message Contract Workflow

```
1. Developer loads input schema (XSD)
   ↓
2. Daemon parses schema → Type Environment
   ↓
3. Developer writes UTL-X transformation
   ↓
4. LSP provides autocomplete for $input.Order.Customer.Name
   ↓
5. Daemon infers output types
   ↓
6. Right panel shows inferred output schema
   ↓
7. Type errors displayed in real-time
   ↓
8. Developer exports output schema for API consumers
```

---

## Execution Mode

### Purpose

Execute transformations with **actual instance data**.

### Left Panel: Input Data

**Displays:**
- XML instance document
- JSON instance document
- CSV data
- Other data formats

**UI Components:**
```typescript
interface InputDataPanel {
    // Data editor
    dataEditor: MonacoEditor;

    // Format selector
    dataFormat: 'xml' | 'json' | 'csv' | 'yaml' | 'auto';

    // Multiple inputs support
    inputs: InputDocument[];

    // Actions
    addInput(input: InputDocument): void;
    removeInput(id: string): void;
    loadFromFile(): Promise<void>;
    validateData(): Promise<boolean>; // Validate against schema if available
}
```

**Example XML Data:**
```xml
<Order id="ORD-12345">
  <Customer>
    <Name>Acme Corp</Name>
  </Customer>
  <Items>
    <Item price="29.99" quantity="2"/>
    <Item price="15.50" quantity="1"/>
  </Items>
</Order>
```

### Middle Panel: UTL-X Transformation

**Same editor, different context:**
- Autocomplete based on actual data structure (if no schema)
- Execution error highlighting
- Execution time profiling

### Right Panel: Output Data

**Displays:**
- Transformed JSON/XML/CSV output
- Execution statistics
- Execution errors

**UI Components:**
```typescript
interface OutputDataPanel {
    // Output viewer
    outputViewer: MonacoEditor; // Or custom renderer

    // View modes
    viewMode: 'pretty' | 'raw' | 'tree';

    // Execution stats
    stats: {
        executionTimeMs: number;
        memoryBytes?: number;
        nodesProcessed?: number;
    };

    // Actions
    exportOutput(): Promise<void>;
    copyToClipboard(): void;
    compareWithExpected(): Promise<void>;
}
```

**Example Output:**
```json
{
  "id": "ORD-12345",
  "customerName": "Acme Corp",
  "totalItems": 2,
  "totalValue": 75.48
}
```

### Execution Workflow

```
1. Developer loads input data (XML)
   ↓
2. Developer writes/edits UTL-X transformation
   ↓
3. Developer clicks "Execute" (or auto-execute)
   ↓
4. Daemon executes transformation
   ↓
5. Right panel shows output data
   ↓
6. Developer verifies correctness
   ↓
7. Execution errors displayed if any
```

---

## Metadata-to-Metadata Transformations

### Important Distinction

**Metadata-to-metadata transformations are NOT Message Contract mode.** They are **Execution-mode transformations** where the schema itself is the data being transformed.

### When UTLX Declares Tier-2 Formats

When your UTLX file declares tier-2 formats as input/output:

```utlx
%utlx 1.0
input xsd        ← Tier-2 format (schema)
output jsch      ← Tier-2 format (schema)
---
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": $input.schema.elements |> map((elem) => {
    (elem.name): {
      "type": mapXsdTypeToJsonType(elem.type)
    }
  })
}
```

### How Theia Extension Handles This

**This is treated as Execution Mode:**

| Panel | Content | Purpose |
|-------|---------|---------|
| **Left** | XSD file (the input DATA) | The schema being transformed |
| **Middle** | UTLX transformation with tier-2 formats | Works with schema constructs (elements, types) |
| **Right** | JSON Schema file (the OUTPUT) | The resulting schema |

**Key Difference from Message Contract:**
- **Message Contract**: Left panel = metadata FOR validation, Middle = `input xml`, Right = inferred schema
- **Metadata-to-Metadata**: Left panel = actual data (happens to be a schema), Middle = `input xsd`, Right = transformed output

### Common Metadata-to-Metadata Use Cases

1. **Schema Format Conversion**
   - XSD → JSON Schema
   - JSON Schema → Avro Schema
   - Protobuf → JSON Schema

2. **Schema Simplification**
   - Remove optional fields
   - Flatten nested structures
   - Extract subset of schema

3. **Schema Generation**
   - Generate TypeScript interfaces from JSON Schema
   - Generate OpenAPI specs from XSD
   - Create GraphQL schemas from JSON Schema

### Example: XSD to JSON Schema Conversion

**Left Panel (order.xsd):**
```xml
<?xml version="1.0"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="Order">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="Customer" type="xs:string"/>
        <xs:element name="Total" type="xs:decimal"/>
      </xs:sequence>
      <xs:attribute name="id" type="xs:string" use="required"/>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

**Middle Panel (transform.utlx):**
```utlx
%utlx 1.0
input xsd
output jsch
---
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": "Order ID from @id attribute"
    },
    "Customer": {
      "type": "string"
    },
    "Total": {
      "type": "number"
    }
  },
  "required": ["id", "Customer", "Total"]
}
```

**Right Panel (order.schema.json):**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": "Order ID from @id attribute"
    },
    "Customer": {
      "type": "string"
    },
    "Total": {
      "type": "number"
    }
  },
  "required": ["id", "Customer", "Total"]
}
```

### Why No Message Contract Mode?

**There's no "meta-meta-data"** to validate against. You would need:
- XSD for XSD (Schema of a schema definition)
- JSON Schema for JSON Schema (Schema of a schema definition)

While these technically exist, they're not practical for validation in this context. The transformation operates on the schema structure itself.

### UI Mode Indication

When the user opens a UTLX file with tier-2 formats:
- Mode selector should be **disabled** or show "Execution (Schema Transformation)"
- Left/Right panels labeled clearly: "Input Schema (Data)" / "Output Schema (Result)"
- No type-checking features (since we're in Execution mode)

---

## Mode Switching & UI

### Mode Selector

```
┌────────────────────────────────────────────────────────┐
│  UTL-X Transformation Editor                           │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Mode:  ⚪ Message Contract  ⚫ Execution                │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  [Left Panel]  [Middle: UTL-X]  [Right Panel]         │
└────────────────────────────────────────────────────────┘
```

### State Transitions

```typescript
interface ModeState {
    currentMode: 'design-time' | 'runtime';

    designTimeState?: {
        inputSchema: Schema;
        outputSchema: Schema;
        typeEnvironment: TypeEnvironment;
        diagnostics: Diagnostic[];
    };

    runtimeState?: {
        inputData: InputDocument[];
        outputData: ExecutionResult;
        executionStats: ExecutionStats;
    };
}

class ModeManager {
    switchToDesignTime() {
        // Save runtime state
        // Load/activate design-time state
        // Update panels
        // Notify LSP daemon
    }

    switchToExecution() {
        // Save design-time state
        // Load/activate runtime state
        // Update panels
        // Notify LSP daemon
    }

    // Hybrid: Use design-time schema + runtime data
    enableSchemaValidation() {
        // Validate runtime data against design-time schema
    }
}
```

### Panel Content Based on Mode and UTLX Format

| UTLX Declaration | Panel | Message Contract Mode | Execution Mode |
|------------------|-------|------------------|--------------|
| `input xml`<br>`output json` | **Left** | XSD Schema (metadata for validation) | XML Data (instance) |
| (tier-1 formats) | **Middle** | UTL-X with type checking | UTL-X with execution |
|  | **Right** | JSON Schema (inferred structure) | JSON Data (transformed result) |
| | | | |
| `input xsd`<br>`output jsch` | **Left** | N/A (no Message Contract mode) | XSD File (the data being transformed) |
| (tier-2 formats) | **Middle** | N/A | UTL-X transforming schema constructs |
|  | **Right** | N/A | JSON Schema (output schema) |

**Key Distinction:**
- **Tier-1 formats**: Two modes available (Message Contract for validation, Execution for running)
- **Tier-2 formats**: Only Execution mode (schema IS the data, not metadata for validation)

### Toolbar Actions

**Message Contract Actions:**
- Load Schema
- Export Output Schema
- Generate Sample Data
- Validate Type System
- View Type Graph

**Execution Actions:**
- Load Input Data
- Execute Transformation
- Export Output
- Compare with Expected
- Performance Profile

---

## Three-Panel Layout (Enhanced)

### Visual Layout

```
┌──────────────────────────────────────────────────────────────────┐
│  File: transform.utlx            Mode: [Contract] [Execution]      │
├────────────┬─────────────────────────────┬────────────────────────┤
│            │                             │                        │
│  INPUT     │    TRANSFORMATION           │      OUTPUT            │
│            │                             │                        │
│  ┌──────┐  │  %utlx 1.0                  │  ┌──────────────────┐  │
│  │Schema│  │  input xml                  │  │ Schema (Contract)│  │
│  │ or   │  │  output json                │  │  or              │  │
│  │Data  │  │  ---                        │  │ Data (Execution)   │  │
│  └──────┘  │                             │  └──────────────────┘  │
│            │  {                          │                        │
│  [View]    │    id: $input.Order.@id,    │  [View]                │
│   ├─ Tree  │    customer:                │   ├─ Tree              │
│   ├─ Raw   │      $input.Order           │   ├─ Pretty            │
│   └─ Src   │        .Customer.Name,      │   └─ Raw               │
│            │    items: count(            │                        │
│  [Actions] │      $input.Order.Items     │  [Actions]             │
│   • Load   │        .Item)               │   • Export             │
│   • Valid  │  }                          │   • Compare            │
│   • Gen    │                             │   • Copy               │
│            │  [LSP Diagnostics]          │                        │
│            │  ✓ Type check passed        │  [Type Info / Stats]   │
│            │  ℹ 2 optional properties    │  ⏱ Inferred in 45ms   │
│            │                             │  📊 4 properties       │
└────────────┴─────────────────────────────┴────────────────────────┘
│  Status: Mode: Message Contract | Schema: order.xsd | Types: Valid ✓  │
└──────────────────────────────────────────────────────────────────┘
```

### Responsive Resizing

- Panels can be resized horizontally
- Minimum width: 200px per panel
- Can collapse left/right panels temporarily
- Full-screen middle panel for focused editing

---

## LSP/Daemon Integration

### Mode-Aware LSP Protocol

#### Initialize with Mode

```json
{
  "method": "initialize",
  "params": {
    "capabilities": {
      "utlxExtensions": {
        "designTimeMode": true,
        "runtimeMode": true,
        "schemaFormats": ["xsd", "json-schema", "proto"]
      }
    }
  }
}
```

#### Set Mode

```json
{
  "method": "utlx/setMode",
  "params": {
    "mode": "design-time",
    "context": {
      "inputSchema": {
        "uri": "file:///schemas/order.xsd",
        "format": "xsd"
      }
    }
  }
}
```

#### Message Contract Type Checking

```json
{
  "method": "utlx/checkTypes",
  "params": {
    "document": "file:///transform.utlx",
    "inputSchema": {...}
  }
}

// Response
{
  "result": {
    "valid": true,
    "outputSchema": {
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "properties": {...}
    },
    "diagnostics": [
      {
        "severity": "info",
        "message": "Optional property 'middleName' may be null",
        "location": {...}
      }
    ]
  }
}
```

#### Execution Execution

```json
{
  "method": "utlx/execute",
  "params": {
    "document": "file:///transform.utlx",
    "inputs": [
      {
        "id": "input",
        "content": "<Order id=\"ORD-001\">...</Order>",
        "format": "xml"
      }
    ]
  }
}

// Response
{
  "result": {
    "success": true,
    "output": "{\"id\":\"ORD-001\",...}",
    "format": "json",
    "executionTimeMs": 23
  }
}
```

### Daemon State Management

```kotlin
class DaemonStateManager {
    // Separate state per mode
    private val designTimeStates = ConcurrentHashMap<String, DesignTimeState>()
    private val runtimeStates = ConcurrentHashMap<String, ExecutionState>()

    data class DesignTimeState(
        val inputSchema: Schema,
        val typeEnvironment: TypeEnvironment,
        val outputSchema: Schema?,
        val diagnostics: List<Diagnostic>
    )

    data class ExecutionState(
        val inputData: List<InputDocument>,
        val executionResult: ExecutionResult?,
        val executionStats: ExecutionStats?
    )

    fun setDesignTimeContext(uri: String, schema: Schema) {
        val typeEnv = schemaParser.parseToTypeEnvironment(schema)
        designTimeStates[uri] = DesignTimeState(
            inputSchema = schema,
            typeEnvironment = typeEnv,
            outputSchema = null,
            diagnostics = emptyList()
        )
    }

    fun inferOutputSchema(uri: String, ast: UTLXNode): Schema? {
        val state = designTimeStates[uri] ?: return null
        return typeInference.inferOutputSchema(ast, state.typeEnvironment)
    }
}
```

---

## Workflow Examples

### Example 1: Schema-First Development (Message Contract)

```
Scenario: API developer needs to transform orders XML to JSON
          but doesn't have sample data yet.

1. Developer receives order.xsd from partner
2. Opens UTL-X Theia extension
3. Switches to Message Contract mode
4. Loads order.xsd in left panel
5. Daemon parses XSD → Type Environment
6. Starts writing transformation in middle panel:

   %utlx 1.0
   input xml
   output json
   ---
   {
     id: $input.Order.@

7. Autocomplete suggests: @id, @status, @date
8. Selects @id, continues:

   {
     id: $input.Order.@id,
     customer: $input.Order.Customer.

9. Autocomplete suggests: Name, Email, Address
10. Completes transformation
11. Right panel shows inferred JSON Schema
12. Exports schema for downstream team
13. Later, when data arrives, switches to Execution mode to test
```

### Example 2: Test-Driven Development (Execution → Message Contract)

```
Scenario: Developer has sample data, wants to ensure transformation
          works for all possible inputs.

1. Developer starts in Execution mode with sample data
2. Writes transformation, verifies output looks correct
3. Switches to Message Contract mode
4. Loads full XSD (covers more cases than sample)
5. Type checker finds edge case:
   "Warning: Item.discount is optional but not handled"
6. Updates transformation to handle optional fields
7. Switches back to Execution, confirms sample still works
8. Exports transformation for production
```

### Example 3: Hybrid Mode (Schema Validation + Execution)

```
Scenario: Developer wants Execution-mode testing with schema validation

1. Loads XSD in Message Contract mode
2. Daemon builds type environment
3. Switches to Execution mode
4. Enables "Validate with Schema" option
5. Loads input data
6. Daemon validates data against XSD before transformation
7. If data is invalid → Error shown before execution
8. If data is valid → Executes transformation
9. Output panel shows both:
   - Transformed data
   - Schema conformance status
```

### Example 4: Metadata-to-Metadata Transformation (Schema Engineering)

```
Scenario: Organization is migrating from SOAP/XML to REST/JSON APIs
          and needs to convert all XSD schemas to JSON Schema.

1. Developer creates UTLX transformation for schema conversion:

   %utlx 1.0
   input xsd        ← Tier-2 format (schema IS the data)
   output jsch      ← Tier-2 format (schema IS the output)
   ---
   {
     "$schema": "http://json-schema.org/draft-07/schema#",
     "type": "object",
     "properties": $input.schema.elements |> map((elem) => {
       (elem.name): {
         "type": mapXsdTypeToJsonType(elem.type),
         "description": elem.annotation.documentation
       }
     }),
     "required": $input.schema.elements
                 |> filter(elem => elem.minOccurs >= 1)
                 |> map(elem => elem.name)
   }

2. Opens Theia extension (automatically detects tier-2 formats)
3. Mode selector is DISABLED (only Execution mode available)
4. Left panel: Loads customer.xsd (the input DATA being transformed)
5. Middle panel: The UTLX transformation above
6. Right panel: Shows resulting customer.schema.json
7. Developer clicks "Execute" (Execution mode - transforming schema)
8. Verifies JSON Schema is correct
9. Repeats for all 50 XSD files in the project

Key Difference from Message Contract:
- This is NOT type-checking a data transformation
- This IS executing a transformation where schemas are the data
- No "Message Contract mode" available (no meta-meta-data exists)
```

---

## Implementation Phases

### Phase 1: Execution Mode (Current)
**Status: Implemented in v1.0**

- ✅ Three-panel layout
- ✅ Input data loading
- ✅ UTL-X editor with syntax highlighting
- ✅ Transformation execution
- ✅ Output display
- ✅ LSP integration (basic)

### Phase 2: Message Contract Foundation
**Estimated: 4-6 weeks**

- [ ] Schema parser (XSD → Type Environment)
- [ ] Type inference engine
- [ ] Output schema generation
- [ ] Mode selector UI
- [ ] Message Contract LSP methods
- [ ] Schema display panels

**Deliverables:**
- Message Contract mode works with XSD
- Type checking in editor
- Output schema inference
- Basic autocomplete from schema

### Phase 3: Enhanced Type System
**Estimated: 3-4 weeks**

- [ ] JSON Schema support
- [ ] Proto schema support
- [ ] Union types
- [ ] Generic types
- [ ] Advanced type inference

**Deliverables:**
- Multi-format schema support
- Rich type errors
- Type hover hints
- Go-to-definition for schema elements

### Phase 4: Hybrid Mode & Validation
**Estimated: 2-3 weeks**

- [ ] Schema + data validation
- [ ] Generate sample data from schema
- [ ] Compare actual vs expected output
- [ ] Performance profiling
- [ ] Schema diff viewer

**Deliverables:**
- Hybrid mode operational
- Schema-based data validation
- Sample data generation
- Advanced testing features

### Phase 5: Developer Experience
**Estimated: 2-3 weeks**

- [ ] Schema tree view
- [ ] Type graph visualization
- [ ] Quick fixes for type errors
- [ ] Refactoring support
- [ ] Export API documentation

**Deliverables:**
- Polished UX
- Advanced IDE features
- Documentation generation
- Production-ready extension

---

## Appendix A: Type Environment Example

### Input: XSD
```xml
<xs:schema>
  <xs:element name="Order">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="Item" maxOccurs="unbounded">
          <xs:complexType>
            <xs:attribute name="price" type="xs:decimal"/>
            <xs:attribute name="qty" type="xs:integer"/>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
      <xs:attribute name="id" type="xs:string"/>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

### Type Environment (Internal Representation)
```kotlin
TypeEnvironment {
  "input" -> ObjectType(
    properties = {
      "Order" -> PropertyInfo(
        type = ObjectType(
          properties = {
            "@id" -> PropertyInfo(StringType, required = true),
            "Item" -> PropertyInfo(
              type = ArrayType(
                elementType = ObjectType(
                  properties = {
                    "@price" -> PropertyInfo(NumberType),
                    "@qty" -> PropertyInfo(IntegerType)
                  }
                )
              ),
              maxOccurs = -1 // unbounded
            )
          }
        )
      )
    }
  )
}
```

### Type Inference in Action
```utlx
sum($input.Order.Item |> map(item => item.@price * item.@qty))
```

**Type Inference Steps:**
1. `$input.Order.Item` → `ArrayType(ObjectType)`
2. `item.@price` → `NumberType`
3. `item.@qty` → `IntegerType`
4. `NumberType * IntegerType` → `NumberType`
5. `map(...) → NumberType` → `ArrayType(NumberType)`
6. `sum(ArrayType(NumberType))` → `NumberType` ✓

---

## Appendix B: UI Component Tree

```
TheiaExtension
├── ModeSelector
│   ├── DesignTimeButton
│   └── ExecutionButton
│
├── ThreePanelLayout
│   ├── LeftPanel (ResizablePanel)
│   │   ├── ModeAwarePanelHeader
│   │   ├── SchemaEditor (Message Contract)
│   │   │   ├── MonacoEditor
│   │   │   ├── SchemaTreeView
│   │   │   └── SchemaActions
│   │   └── DataEditor (Execution)
│   │       ├── MonacoEditor
│   │       ├── InputList
│   │       └── DataActions
│   │
│   ├── MiddlePanel (UTLXEditor)
│   │   ├── MonacoEditor (with LSP)
│   │   ├── DiagnosticsPanel
│   │   └── EditorToolbar
│   │
│   └── RightPanel (ResizablePanel)
│       ├── ModeAwarePanelHeader
│       ├── SchemaViewer (Message Contract)
│       │   ├── JSONSchemaDisplay
│       │   ├── SchemaTreeView
│       │   └── SchemaActions
│       └── OutputViewer (Execution)
│           ├── OutputDisplay
│           ├── ExecutionStats
│           └── OutputActions
│
└── StatusBar
    ├── ModeIndicator
    ├── SchemaIndicator (Message Contract)
    ├── TypeCheckStatus
    └── ExecutionStats (Execution)
```

---

## Process Lifecycle & Service Supervision

The IDE is not a single process. It orchestrates backing services, each a child
of the Theia backend:

| Service | What it is | Default port | Notes |
|---|---|---|---|
| `utlxd` | Language/transform daemon (JVM jar) | API 7779, LSP 7777 | Validate, execute, infer-schema, completions; serves the IDE |
| UTLX MCP server | Node process (AI assist) | 7780 | Calls `utlxd` over HTTP; provider = claude-code |
| (Theia backend) | Node host | 4000 (web) | The supervisor of the two above |

**Node is the supervisor — not an OS service manager.** Because the services
must live and die *with* the IDE, the correct supervisor is the long-lived Theia
backend (Node), which spawns them via `child_process.spawn`. This lives in
`utlx-theia-extension/src/node/services/service-lifecycle-manager.ts` (bound as a
Theia `BackendApplicationContribution`, so its hooks fire on backend start/stop).

We deliberately **do not** use a Java service wrapper (Tanuki, YAJSW, jsvc).
Those exist to register a JVM as an *independent* OS-level service that survives
reboots and is supervised by the OS — the opposite of what we want. Adding one
would create a second supervisor competing with Node and make orphaning *harder*
to reason about. Everything a wrapper provides we implement in the lifecycle
manager:

- **Start ordering & readiness gating** — start `utlxd`, poll `/api/health` until
  ready, *then* start the MCP server (which depends on it). Present as
  `waitForUTLXD()`.
- **Restart-with-backoff** — on unexpected child exit, respawn with exponential
  backoff, a max-retry cap, and crash-loop detection. (Currently a TODO in the
  manager — the gap to close.)
- **Graceful shutdown** — SIGTERM, then SIGKILL after a timeout, on backend stop.
- **Idempotent daemon start** — reuse a healthy `utlxd` if already running (the
  heavy JVM should not restart on every IDE rebuild); only the MCP server is
  cheap enough to recycle each cycle.

Configuration is environment-driven (`UTLXD_REST_PORT`, `UTLXD_LSP_PORT`,
`UTLX_MCP_PORT`, `AUTO_START_SERVICES`, `MCP_SERVER_PATH`, `UTLXD_JAR_PATH`), so
the same code path works in dev (script-launched) and in a packaged app.

## Process Watchdog: Die-With-Parent

Node's exit handlers cover *graceful* shutdown. They do **not** cover the case
that has repeatedly bitten us: the parent (Theia/Electron) is `kill -9`'d or
crashes. The OS does not reap the children, so `utlxd` and the MCP server orphan
and keep holding their ports — the next launch then hits `EADDRINUSE` and may
silently keep talking to a stale process.

The robust fix is a **die-with-parent watchdog inside each child**, independent of
how the parent dies:

- At spawn, the parent passes its PID (and/or an inherited pipe file descriptor).
- The child runs a small watchdog: if the pipe closes (EOF) or the parent PID
  disappears, it `exit()`s immediately.
- Portable mechanism: Linux has `PR_SET_PDEATHSIG`, but macOS has no equivalent,
  so the pipe/PID-watch is the cross-platform approach — ~30 lines in the daemon
  and a few on the Node side.

**The watchdog is runtime- and packaging-agnostic.** It is about *process
topology*, not whether the daemon is a JVM jar or a native binary, and not about
Electron vs. web. A native `utlxd` spawned by Node is still an orphan-able child,
so the watchdog is required **regardless of any future GraalVM decision** —
compiling to native does not remove the need for it.

Together with the supervisor's port-cleanup-on-start, this closes the orphan
class of bugs in both directions: parent-watches-child (restart) and
child-watches-parent (self-exit).

## Daemon Runtime: JVM Jar, Bundled JRE, and the Native Option

Two engines make opposite runtime choices, for good reasons:

- **`utlxe` (production engine) stays a JVM jar — permanently.** Its COMPILED
  strategy generates JVM bytecode at runtime via ASM. GraalVM native-image is
  closed-world AOT and cannot define classes at runtime, so the engine's headline
  optimization is fundamentally incompatible with native compilation.
- **`utlxd` (IDE daemon) is a JVM jar today and is the *candidate* for a native
  build** — because the IDE does not need the COMPILED strategy. Validate,
  infer-schema, completions, and single-message live preview all run on the
  interpreter/TEMPLATE path, which produces identical output. The CLI is already
  GraalVM-native for the same reason (one-shot, no runtime bytecode generation).

**Packaging the jar (today): bundle a JRE.** A desktop app cannot assume `java`
is on `PATH`. Use `jlink` to produce a trimmed runtime with only the modules
`utlxd` needs (tens of MB, not a full JDK), place it in app resources, and spawn
it explicitly: `<resources>/jre/bin/java -jar utlxd.jar …`. This is a packaging
step, not a service wrapper.

**The native `utlxd` option (future, Electron-driven).** The attraction is real:
no bundled JRE, sub-100ms startup, smaller installer. The blocker is **not** ASM
(utlxd would run interpreter-only) — it is the daemon's dependencies under
native-image: Ktor (REST), the LSP transport, and Jackson all use
reflection / dynamic proxies / resource loading that need explicit native-image
reachability metadata (`reflect-config.json`, resource config), plus a
per-platform native build matrix and ongoing maintenance. So native `utlxd` is a
dependency-reachability *project*, gated on (a) guaranteeing `utlxd` never touches
the COMPILED path and (b) getting those libraries native-clean. It does not change
the watchdog or supervision design.

Recommendation: ship the jar + bundled JRE now; treat native `utlxd` as a later
optimization that mainly benefits Electron installer size and cold start.

## Electron Desktop Application

Two delivery shells share the same extension and backend code:

- **Web (`browser-app`, exists today):** Theia served over HTTP, opened in a
  browser. Best for cloud/hosted use. The frontend must never touch service ports
  directly — health/status flow through the backend over JSON-RPC
  (`getServicesHealth()`), because the browser's `localhost` is not the server's.
- **Desktop (`electron-app`, to add):** Theia packaged as an Electron app for a
  one-double-click full IDE experience, with `utlxd` + MCP bundled inside.

**Versioning:** add an `electron-app` package beside `browser-app`, with every
`@theia/*` dependency pinned to the same version the extension already uses
(mixing Theia versions across the two apps is the primary source of breakage). Do
**not** choose an Electron version directly — take the one Theia pins via
`@theia/electron` and pin that exact value. Build the toolchain on the matching
Node LTS.

**Native modules & ASAR:** `node-pty` (terminal) and any spawned binaries must be
rebuilt for Electron's ABI (`@electron/rebuild`). In packaging, `asarUnpack` the
JRE, `utlxd.jar`, the MCP server, and `node-pty` — ASAR archives break process
spawning and file execution.

**Lifecycle in Electron:** the Electron *main* process owns startup; the Theia
*backend* (Node) remains the supervisor of `utlxd` + MCP exactly as in the web
case. The die-with-parent watchdog matters most here, since users quit/kill the
desktop app directly.

## Per-Platform Installers (macOS, Windows, Linux)

Goal: ship `utlxd` (plus the MCP server and bundled JRE) *inside* a signed,
double-click installer per platform, so an end user gets the full IDE without
installing Java, Node, or the daemon separately. Use **`electron-builder`** (or
Theia's electron packaging) to produce all three from one config.

| Platform | Artifact(s) | Signing / hardening |
|---|---|---|
| macOS | `.dmg` (and/or `.pkg`); universal arm64 + x64 | Developer ID code-signing **and notarization** (Gatekeeper); hardened runtime |
| Windows | NSIS `.exe` and/or `.msi`; Squirrel auto-update | Authenticode signing (EV cert recommended to avoid SmartScreen) |
| Linux | `AppImage` (portable) + `.deb`/`.rpm` | No mandatory signing; optional GPG for repos |

Cross-cutting requirements:

- **Bundle the runtime per platform.** Each installer carries the matching `jlink`
  JRE for that OS/arch (or, later, the native `utlxd` binary for that target).
  Build on each target OS / per arch — native modules and the JRE are not
  portable across platforms.
- **Spawn by absolute path from app resources**, never relying on `PATH`. Resolve
  `utlxd.jar`, the JRE, and the MCP server relative to the app's (asarUnpacked)
  resource directory.
- **Ports & single-instance:** keep the env-configurable ports; add a
  single-instance lock so two windows don't fight over `7777/7779/7780`.
- **Auto-update** (optional): electron-builder supports per-platform update feeds;
  the daemon/JRE ship inside the app bundle so they update atomically with the IDE.

This packaging path is what turns "Theia extension + daemon" into a shippable
product: one installer per platform, `utlxd` embedded, no external prerequisites.

## Bundle-Level IDE: Editing & Testing a `.utlar` Project

### The gap: the IDE edits one transformation, production runs a bundle

Today the IDE models a **single** transformation (Input | Transformation | Output).
But the unit that the engine loads, that CI/CD ships, and that the book describes
is the **`.utlar` bundle** — an *integration project*, not one transformation. A
bundle (see `EF09-production-bundle-mode.md`, `BundleLoader.kt`) contains:

```
bundle.utlar (ZIP):
  manifest.json                 ← aggregate: transformations + schemas + messaging topology
  transformations/
    orders-in/
      orders-in.utlx            ← the transformation source
      transform.yaml            ← strategy, validation policy, messaging in/out, schema refs
    invoice-to-ubl/
      invoice-to-ubl.utlx
      transform.yaml
  schemas/                      ← SHARED across transformations
    order.json
    invoice.xsd
```

So the IDE is missing the entire **project/bundle tier** above the editor.
Authoring or testing what you actually ship requires closing that gap.

### Layered model

```
Bundle (.utlar)         project: manifest + shared schemas + messaging topology   ← MISSING
  └─ Transformation     orders-in / invoice-to-ubl … (N of them)                  ← IDE handles ONE
       └─ I / T / O      the current three-panel editor                           ← EXISTS
```

### Decision: navigate N, do not run N live editors

We support **N transformations in one project — navigable and individually
editable/testable — but NOT N concurrent Input|Transformation|Output triptychs.**
The literal "N live editors" is a UX and resource trap (N daemons / N previews /
confusing layout). Instead we use the standard IDE pattern:

- A **bundle explorer** (project tree) on the left: transformations, shared
  `schemas/`, and the manifest.
- Open transformations as **tabs**; the three-panel I/T/O view binds to the
  **active** transformation.
- You move freely between many transformations and edit them all, but the heavy
  three-panel apparatus (and live preview / daemon execution) operates on one at
  a time.

This is how every IDE handles "a project with many files," and it keeps the
existing three-panel design intact rather than multiplying it.

### New surfaces this introduces

Beyond the existing I/T/O editor, a bundle needs UI that has **no home today**:

- **Bundle explorer** — the project tree (transformations + schemas + manifest).
- **Shared `schemas/` view** — one schema set referenced by many `transform.yaml`s
  (project-level, not per-transformation).
- **`transform.yaml` editor** — strategy, validation policy, and **messaging
  in/out** (queue/topic names) per transformation. The IDE currently has no UI
  for any of this.
- **Manifest / messaging-topology view** — the queue/topic wiring *across*
  transformations. This is the one genuinely new view beyond I/T/O: a pipeline /
  topology diagram of how the bundle's transformations connect.
- **Bundle build & test** — Build/Export `.utlar`; **Validate All** and **Test
  All** (run a sample through each transformation, check schema refs resolve, lint
  the manifest) — "test the bundle," not just one transformation.

### Theia menu & command expansion

Theia's contribution model (menus, commands, keybindings, view-containers) covers
this directly. Add a top-level **UTL-X** (Bundle) menu + command-palette entries:

- New Bundle · Open Bundle · Build `.utlar` · Validate Bundle · Test All
- New Transformation (scaffolds `transformations/<name>/` + `transform.yaml`)
- Add Schema · Edit `transform.yaml` · Edit Manifest

The menu/command surface is low-risk and Theia-native; the *views* behind some
commands (topology diagram, manifest editor) are the real implementation work.

### Phasing

- **Phase A — Bundle as project (highest value):** recognize/open a `.utlar`
  project, bundle explorer tree, open transformations as tabs (active one drives
  the three panels), Build/Export `.utlar`, shared `schemas/` view. Mostly
  Theia-native (a `.utlar` project is a folder with a known structure); this is
  the bridge to CI/CD.
- **Phase B — Per-transformation config:** `transform.yaml` editor
  (strategy / validation / messaging) surfaced in the IDE.
- **Phase C — Bundle-level operations:** Validate-All / Test-All, manifest view,
  and the messaging **topology diagram** (the one truly new view). The UT­L-X menu
  + commands land across these phases.

This is a deliberate scope expansion from "transformation editor" to "integration
project IDE," aligned with how the engine already runs (`BundleLoader` / EF09) and
how bundles are shipped via CI/CD.

## Conclusion

This enhanced Theia extension design provides:

1. **Message Contract Mode**: Schema-based type checking and validation
   - **Applies to**: Tier-1 data format transformations (`input xml/json/yaml/csv`)
   - **Purpose**: Validate transformation logic against schemas before execution
   - **Benefit**: Catch type errors early, generate output schemas, enable autocomplete

2. **Execution Mode**: Instance data transformation and testing
   - **Applies to**: All transformations (tier-1 and tier-2)
   - **Purpose**: Execute transformations on actual data
   - **Benefit**: Test with real data, debug, performance profiling

3. **Metadata-to-Metadata Transformations**: Schema engineering (Execution mode only)
   - **Applies to**: Tier-2 format transformations (`input xsd/jsch/avsc/proto`)
   - **Purpose**: Convert between schema formats, generate schemas
   - **Constraint**: No Message Contract mode available (schema IS the data, not metadata)

4. **Seamless mode switching**: Easy toggle between Message Contract and Execution (for tier-1)

5. **LSP integration**: Mode-aware language server protocol with format detection

6. **Developer productivity**: Autocomplete, type hints, error detection tailored to mode

7. **API contract validation**: Generate and verify schemas in Message Contract mode

### Key Scope Clarifications

**What Message Contract Mode Is:**
- Type-checking tier-1 data transformations using tier-1 schemas as metadata
- Validating that `input xml` transformations are type-safe given an XSD
- Inferring output structure without executing the transformation

**What Message Contract Mode Is NOT:**
- NOT for tier-2 metadata-to-metadata transformations (`input xsd`)
- NOT a replacement for Execution-mode testing (both modes are essential)
- NOT applicable when schemas don't exist or aren't available

The design supports both **schema-first** and **test-first** development workflows for tier-1 transformations, plus **schema engineering** workflows for tier-2 transformations, giving developers maximum flexibility in how they build and validate UTL-X transformations.

---

**Document Version:** 3.0
**Last Updated:** 2026-05-31
**Supersedes:** theia-extension-api-reference.md v1.0 (partially)
**Related Documents:**
- design-time-schema-analysis-enhanced.md
- theia-extension-implementation-guide.md
- cli-daemon-split-architecture.md (CLI vs daemon split; native CLI)
- ide-modes-specification.md (Execution vs Message Contract modes)
- io-theia-explained.md

# UTL-X Theia Extension - Design-Time & Runtime Architecture

**Version:** 2.0
**Date:** 2025-11-01
**Status:** Design Proposal - Enhanced with Design-Time Mode
**Authors:** UTL-X Architecture Team

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Design-Time vs Runtime Distinction](#design-time-vs-runtime-distinction)
3. [Architecture Overview](#architecture-overview)
4. [Design-Time Mode](#design-time-mode)
5. [Runtime Mode](#runtime-mode)
6. [Mode Switching & UI](#mode-switching--ui)
7. [Three-Panel Layout (Enhanced)](#three-panel-layout-enhanced)
8. [LSP/Daemon Integration](#lspdaemon-integration)
9. [Workflow Examples](#workflow-examples)
10. [Implementation Phases](#implementation-phases)

---

## Executive Summary

### What Changed from v1.0?

The original Theia extension design (v1.0) focused only on **runtime execution**:
- Left panel: Input instance data (XML, JSON)
- Middle panel: UTL-X transformation
- Right panel: Output instance data (JSON, XML)

This design is enhanced with **Design-Time Mode** where:
- Left panel: Input **schema/metadata** (XSD, JSON Schema)
- Middle panel: UTL-X transformation (with type checking)
- Right panel: Output **schema/metadata** (inferred JSON Schema)

### Key Benefits

**Design-Time Mode:**
- ✅ Catch type errors before deployment
- ✅ Validate API contracts
- ✅ Autocomplete based on schema structure
- ✅ Generate output schema for downstream consumers
- ✅ No need for representative test data

**Runtime Mode:**
- ✅ Test with actual data instances
- ✅ Debug transformation logic
- ✅ Verify output format
- ✅ Performance testing

---

## Design-Time vs Runtime Distinction

### Fundamental Difference

```
┌─────────────────────────────────────────────────────────────────┐
│ DESIGN-TIME: Schema/Metadata Analysis                          │
│                                                                 │
│  Input Schema (XSD)    →    UTL-X Transform    →    Output     │
│                                                      Schema     │
│  <xs:element           →    %utlx 1.0          →    {          │
│    name="Order"        →    input xml          →      "$schema"│
│    type="OrderType"/>  →    output json        →      "props": │
│                        →    ---                →        {       │
│  <xs:complexType       →    {                  →          "id": │
│    name="OrderType">   →      id: $input      →          {...} │
│    <xs:attribute       →           .Order     →        }       │
│      name="id"         →           .@id,      →    }           │
│      type="xs:string"  →      ...             →                │
│    />                  →    }                  →                │
│  </xs:complexType>     →                       →                │
│                                                                 │
│  Type Check: $input.Order.@id returns String ✓                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ RUNTIME: Instance Data Execution                               │
│                                                                 │
│  Input Data (XML)      →    UTL-X Transform    →    Output     │
│                                                      Data       │
│  <Order id="ORD-001">  →    %utlx 1.0          →    {          │
│    <Customer>          →    input xml          →      "id":    │
│      <Name>John</Name> →    output json        →      "ORD-001"│
│    </Customer>         →    ---                →      ...      │
│    <Items>             →    {                  →    }          │
│      <Item price="10"  →      id: $input      →                │
│            qty="2"/>   →           .Order     →                │
│    </Items>            →           .@id,      →                │
│  </Order>              →      ...             →                │
│                        →    }                  →                │
│                                                                 │
│  Execute: Transform actual data values                         │
└─────────────────────────────────────────────────────────────────┘
```

### When to Use Each Mode

| Use Design-Time When... | Use Runtime When... |
|-------------------------|---------------------|
| Building a new transformation | Testing with real data |
| Validating API contracts | Debugging transformation logic |
| Generating documentation | Performance testing |
| No test data available yet | Verifying edge cases |
| Early in development cycle | Late in development/production |
| Schema-driven development | Instance-driven development |

---

## Architecture Overview

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    Theia Frontend (Browser)                      │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Mode Selector: [Design-Time] [Runtime]                   │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─────────────┬────────────────────┬────────────────────────┐  │
│  │    Left     │      Middle        │        Right           │  │
│  │    Panel    │   (UTL-X Editor)   │        Panel           │  │
│  │             │    with LSP        │                        │  │
│  │             │                    │                        │  │
│  │  DESIGN:    │                    │  DESIGN:               │  │
│  │  Schema/    │  %utlx 1.0        │  Output                │  │
│  │  Metadata   │  input xml        │  Schema                │  │
│  │  (XSD,      │  output json      │  (JSON Schema)         │  │
│  │  JSON       │  ---              │                        │  │
│  │  Schema)    │  {                │  + Type errors         │  │
│  │             │    id: $input...  │  + Validation          │  │
│  │  RUNTIME:   │  }                │                        │  │
│  │  Instance   │                   │  RUNTIME:              │  │
│  │  Data       │                   │  Output                │  │
│  │  (XML,JSON) │                   │  Data                  │  │
│  └─────────────┴────────────────────┴────────────────────────┘  │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Status Bar:                                               │  │
│  │  Mode: Design-Time | Schema: orders.xsd | Type Check: ✓   │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
                              ↕ JSON-RPC (LSP)
┌──────────────────────────────────────────────────────────────────┐
│                   UTL-X Daemon (Background)                      │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Mode-Aware State Manager                                  ││
│  │                                                             ││
│  │  Design-Time State:                Runtime State:          ││
│  │  - Input schema (XSD)          - Input data instances      ││
│  │  - Type environment            - Execution context         ││
│  │  - Inferred output schema      - Output data               ││
│  │  - Type errors/warnings        - Runtime errors            ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Analysis Engine                                            ││
│  │  ┌──────────────────┐  ┌──────────────────┐                ││
│  │  │ Design-Time:     │  │ Runtime:         │                ││
│  │  │ - Schema Parser  │  │ - Data Parser    │                ││
│  │  │ - Type Inference │  │ - Transformer    │                ││
│  │  │ - Type Checker   │  │ - Serializer     │                ││
│  │  └──────────────────┘  └──────────────────┘                ││
│  └─────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

---

## Design-Time Mode

### Purpose

Analyze transformations using **schema metadata** instead of instance data.

### Left Panel: Input Schema

**Displays:**
- XSD (XML Schema Definition)
- JSON Schema
- YAML Schema
- Proto definitions
- Other schema formats

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

**LSP Messages (Design-Time):**
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

### Design-Time Workflow

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

## Runtime Mode

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
- Runtime error highlighting
- Execution time profiling

### Right Panel: Output Data

**Displays:**
- Transformed JSON/XML/CSV output
- Execution statistics
- Runtime errors

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

### Runtime Workflow

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
7. Runtime errors displayed if any
```

---

## Mode Switching & UI

### Mode Selector

```
┌────────────────────────────────────────────────────────┐
│  UTL-X Transformation Editor                           │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Mode:  ⚪ Design-Time  ⚫ Runtime                │  │
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

    switchToRuntime() {
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

### Panel Content Based on Mode

| Panel | Design-Time Mode | Runtime Mode |
|-------|------------------|--------------|
| **Left** | Input Schema (XSD, JSON Schema) | Input Data (XML, JSON) |
| **Middle** | UTL-X with type checking | UTL-X with execution |
| **Right** | Output Schema (inferred) | Output Data (transformed) |

### Toolbar Actions

**Design-Time Actions:**
- Load Schema
- Export Output Schema
- Generate Sample Data
- Validate Type System
- View Type Graph

**Runtime Actions:**
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
│  File: transform.utlx              Mode: [Design] [Runtime]      │
├────────────┬─────────────────────────────┬────────────────────────┤
│            │                             │                        │
│  INPUT     │    TRANSFORMATION           │      OUTPUT            │
│            │                             │                        │
│  ┌──────┐  │  %utlx 1.0                  │  ┌──────────────────┐  │
│  │Schema│  │  input xml                  │  │ Schema (Design)  │  │
│  │ or   │  │  output json                │  │  or              │  │
│  │Data  │  │  ---                        │  │ Data (Runtime)   │  │
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
│  Status: Mode: Design-Time | Schema: order.xsd | Types: Valid ✓  │
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

#### Design-Time Type Checking

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

#### Runtime Execution

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
    private val runtimeStates = ConcurrentHashMap<String, RuntimeState>()

    data class DesignTimeState(
        val inputSchema: Schema,
        val typeEnvironment: TypeEnvironment,
        val outputSchema: Schema?,
        val diagnostics: List<Diagnostic>
    )

    data class RuntimeState(
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

### Example 1: Schema-First Development (Design-Time)

```
Scenario: API developer needs to transform orders XML to JSON
          but doesn't have sample data yet.

1. Developer receives order.xsd from partner
2. Opens UTL-X Theia extension
3. Switches to Design-Time mode
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
13. Later, when data arrives, switches to Runtime mode to test
```

### Example 2: Test-Driven Development (Runtime → Design-Time)

```
Scenario: Developer has sample data, wants to ensure transformation
          works for all possible inputs.

1. Developer starts in Runtime mode with sample data
2. Writes transformation, verifies output looks correct
3. Switches to Design-Time mode
4. Loads full XSD (covers more cases than sample)
5. Type checker finds edge case:
   "Warning: Item.discount is optional but not handled"
6. Updates transformation to handle optional fields
7. Switches back to Runtime, confirms sample still works
8. Exports transformation for production
```

### Example 3: Hybrid Mode (Schema Validation + Runtime)

```
Scenario: Developer wants runtime testing with schema validation

1. Loads XSD in Design-Time mode
2. Daemon builds type environment
3. Switches to Runtime mode
4. Enables "Validate with Schema" option
5. Loads input data
6. Daemon validates data against XSD before transformation
7. If data is invalid → Error shown before execution
8. If data is valid → Executes transformation
9. Output panel shows both:
   - Transformed data
   - Schema conformance status
```

---

## Implementation Phases

### Phase 1: Runtime Mode (Current)
**Status: Implemented in v1.0**

- ✅ Three-panel layout
- ✅ Input data loading
- ✅ UTL-X editor with syntax highlighting
- ✅ Runtime execution
- ✅ Output display
- ✅ LSP integration (basic)

### Phase 2: Design-Time Foundation
**Estimated: 4-6 weeks**

- [ ] Schema parser (XSD → Type Environment)
- [ ] Type inference engine
- [ ] Output schema generation
- [ ] Mode selector UI
- [ ] Design-time LSP methods
- [ ] Schema display panels

**Deliverables:**
- Design-time mode works with XSD
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
│   └── RuntimeButton
│
├── ThreePanelLayout
│   ├── LeftPanel (ResizablePanel)
│   │   ├── ModeAwarePanelHeader
│   │   ├── SchemaEditor (Design-Time)
│   │   │   ├── MonacoEditor
│   │   │   ├── SchemaTreeView
│   │   │   └── SchemaActions
│   │   └── DataEditor (Runtime)
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
│       ├── SchemaViewer (Design-Time)
│       │   ├── JSONSchemaDisplay
│       │   ├── SchemaTreeView
│       │   └── SchemaActions
│       └── OutputViewer (Runtime)
│           ├── OutputDisplay
│           ├── ExecutionStats
│           └── OutputActions
│
└── StatusBar
    ├── ModeIndicator
    ├── SchemaIndicator (Design-Time)
    ├── TypeCheckStatus
    └── ExecutionStats (Runtime)
```

---

## Conclusion

This enhanced Theia extension design provides:

1. **Design-Time Mode**: Schema-based type checking and validation
2. **Runtime Mode**: Instance data transformation and testing
3. **Seamless switching**: Easy toggle between modes
4. **LSP integration**: Mode-aware language server protocol
5. **Developer productivity**: Autocomplete, type hints, error detection
6. **API contract validation**: Generate and verify schemas

The design supports both **schema-first** and **test-first** development workflows, giving developers flexibility in how they build and validate UTL-X transformations.

---

**Document Version:** 2.0
**Last Updated:** 2025-11-01
**Supersedes:** theia-extension-api-reference.md v1.0 (partially)
**Related Documents:**
- design-time-schema-analysis-enhanced.md
- theia-extension-implementation-guide.md
- io-theia-explained.md

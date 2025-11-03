# MCP-Assisted UTLX Transformation Generation

**Version:** 1.0
**Date:** 2025-11-02
**Status:** Architecture Proposal
**Authors:** UTL-X Architecture Team

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Problem Statement](#problem-statement)
3. [Solution Overview](#solution-overview)
4. [MCP Architecture](#mcp-architecture)
5. [MCP Tool Specifications](#mcp-tool-specifications)
6. [Prompt Engineering Strategy](#prompt-engineering-strategy)
7. [Theia IDE Integration](#theia-ide-integration)
8. [Workflow Examples](#workflow-examples)
9. [Implementation Roadmap](#implementation-roadmap)
10. [Security & Privacy Considerations](#security--privacy-considerations)

---

## Executive Summary

### The Opportunity

Users want to create UTLX transformations but face a steep learning curve:
- **Input**: Data files (JSON, XML, CSV) + schemas (XSD, JSON Schema)
- **Functional Requirements**: Natural language description (e.g., "Convert orders to invoices, calculate totals")
- **Current Gap**: Must manually write UTLX code

### The Solution

Leverage **Model Context Protocol (MCP)** to enable LLM-assisted UTLX generation:
- User provides input data/schema + natural language prompt
- LLM generates UTLX transformation using MCP tools
- System validates and executes transformation
- User refines via iterative conversation

### Key Benefits

1. **Dramatically lowers learning curve**: Describe intent, not syntax
2. **Type-safe generation**: Validation against schemas prevents errors
3. **Leverages existing infrastructure**: LSP daemon, stdlib, type system
4. **Iterative refinement**: Multi-turn conversation for complex transformations
5. **Knowledge accumulation**: Auto-captured examples improve over time

---

## Problem Statement

### Current User Journey (Manual)

```
1. Developer receives requirements: "Transform XML orders to JSON invoices"
2. Developer learns UTLX syntax (hours/days)
3. Developer reads input XSD to understand structure
4. Developer manually writes transformation:
   %utlx 1.0
   input xml
   output json
   ---
   {
     invoiceId: $input.Order.@id,
     ...
   }
5. Developer tests, debugs, iterates
6. Time investment: Hours for first transformation
```

### Pain Points

1. **Learning Curve**: UTLX syntax, functional programming concepts, stdlib functions
2. **Schema Understanding**: Manually inspecting XSD/JSON Schema to find paths
3. **Function Discovery**: Browsing stdlib docs to find `sum`, `map`, etc.
4. **Type Errors**: Trial-and-error to match types correctly
5. **Best Practices**: No guidance on idiomatic UTLX patterns

### Desired User Journey (AI-Assisted)

```
1. Developer receives requirements: "Transform XML orders to JSON invoices"
2. Developer opens Theia IDE, loads input XSD
3. Developer types natural language prompt:
   "Generate JSON invoices with customer name and total calculated from items"
4. AI generates UTLX transformation in <10 seconds
5. Developer reviews, tests, optionally refines
6. Time investment: Minutes instead of hours
```

---

## Solution Overview

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                       Theia IDE (Browser)                           │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────────┐│
│  │  Three-Panel Layout                                            ││
│  │  ┌────────┬─────────────────┬────────┐                        ││
│  │  │ Input  │  UTLX Editor    │ Output │                        ││
│  │  │ Schema │                 │ Schema │                        ││
│  │  └────────┴─────────────────┴────────┘                        ││
│  └────────────────────────────────────────────────────────────────┘│
│                                                                     │
│  ┌────────────────────────────────────────────────────────────────┐│
│  │ 🤖 AI Assistant Panel (NEW)                                    ││
│  │                                                                ││
│  │ Prompt: ┌──────────────────────────────────────────────────┐  ││
│  │         │ Convert XML orders to JSON invoices, calculate  │  ││
│  │         │ total from items                                 │  ││
│  │         └──────────────────────────────────────────────────┘  ││
│  │                                                                ││
│  │ [Generate UTLX]  [Refine]  [Explain]  [Optimize]              ││
│  │                                                                ││
│  │ Generated UTLX: ✓ Validated | ⚠ 2 warnings                    ││
│  └────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
                              ↕ MCP Protocol (JSON-RPC)
┌─────────────────────────────────────────────────────────────────────┐
│                    UTLX MCP Server (NEW Component)                  │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  MCP Tools                                                      ││
│  │  1. get_input_schema(format, content) → structure              ││
│  │  2. get_stdlib_functions(category?) → function list            ││
│  │  3. validate_utlx(transformation) → errors/warnings            ││
│  │  4. infer_output_schema(utlx, input) → output schema           ││
│  │  5. execute_transformation(utlx, data) → result                ││
│  │  6. get_examples(input_type, output_type) → example UTLXs      ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  LLM Integration                                                ││
│  │  - System prompt templates                                      ││
│  │  - Few-shot examples                                            ││
│  │  - Validation feedback loop                                     ││
│  │  - Multi-turn conversation state                                ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
                              ↕ Direct API Calls
┌─────────────────────────────────────────────────────────────────────┐
│                   UTL-X Daemon (Existing)                           │
│                                                                     │
│  - Schema parsing (XSD, JSON Schema, Avro, Proto)                  │
│  - Type inference engine                                           │
│  - Runtime execution                                               │
│  - Stdlib function registry (400+ functions)                       │
│  - LSP server (autocomplete, diagnostics)                          │
└─────────────────────────────────────────────────────────────────────┘
                              ↕ LLM API
┌─────────────────────────────────────────────────────────────────────┐
│                   LLM Service (Claude API, OpenAI, etc.)            │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Components

1. **Theia AI Assistant Panel**: New UI panel for prompt input and interaction
2. **UTLX MCP Server**: Standalone service implementing MCP protocol
3. **MCP Tools**: 6 core tools providing context to LLM
4. **LLM Service**: Claude API (primary), OpenAI compatible (fallback)
5. **UTL-X Daemon**: Existing component providing validation and execution

---

## MCP Architecture

### What is Model Context Protocol (MCP)?

MCP is a protocol developed by Anthropic to enable LLMs to:
- Access external tools and resources
- Retrieve contextual information dynamically
- Execute functions with structured inputs/outputs
- Maintain conversation state across interactions

### Why MCP for UTLX?

| Challenge | MCP Solution |
|-----------|-------------|
| LLM doesn't know UTLX stdlib functions | `get_stdlib_functions()` retrieves function registry |
| LLM doesn't know input data structure | `get_input_schema()` parses XSD/JSON Schema |
| Generated code may have type errors | `validate_utlx()` type-checks before returning |
| No examples in LLM training data | `get_examples()` retrieves conformance suite tests |
| Need to verify output | `execute_transformation()` runs on sample data |
| Output schema inference | `infer_output_schema()` generates JSON Schema |

### MCP Server Technology Stack

**Option A: TypeScript/Node.js**
- Pros: Native MCP SDK support, fast development, web ecosystem
- Cons: Separate runtime from Kotlin daemon

**Option B: Kotlin/JVM**
- Pros: Share code with daemon, type safety, performance
- Cons: MCP SDK immature, more boilerplate

**Recommendation**: **TypeScript** for MCP server, calls Kotlin daemon via HTTP/gRPC

---

## MCP Tool Specifications

### Tool 1: get_input_schema

**Purpose**: Parse input data/schema and extract structure for LLM understanding

```json
{
  "name": "get_input_schema",
  "description": "Parse input schema (XSD, JSON Schema, etc.) and extract structure, types, and valid selector paths for UTLX generation",
  "input_schema": {
    "type": "object",
    "properties": {
      "format": {
        "type": "string",
        "enum": ["xml", "json", "xsd", "jsch", "avsc", "proto"],
        "description": "Format of the input"
      },
      "content": {
        "type": "string",
        "description": "Content of the schema or sample data"
      },
      "mode": {
        "type": "string",
        "enum": ["schema", "instance"],
        "default": "schema",
        "description": "Whether content is a schema or instance data"
      }
    },
    "required": ["format", "content"]
  }
}
```

**Response Example**:
```json
{
  "structure": {
    "Order": {
      "type": "object",
      "attributes": {
        "@id": {"type": "string", "required": true}
      },
      "elements": {
        "Customer": {
          "type": "object",
          "elements": {
            "Name": {"type": "string"}
          }
        },
        "Items": {
          "type": "object",
          "elements": {
            "Item": {
              "type": "array",
              "attributes": {
                "@price": {"type": "decimal"},
                "@quantity": {"type": "integer"}
              }
            }
          }
        }
      }
    }
  },
  "valid_paths": [
    "$input.Order",
    "$input.Order.@id",
    "$input.Order.Customer",
    "$input.Order.Customer.Name",
    "$input.Order.Items.Item",
    "$input.Order.Items.Item[].@price",
    "$input.Order.Items.Item[].@quantity"
  ],
  "root_element": "Order"
}
```

### Tool 2: get_stdlib_functions

**Purpose**: Retrieve available UTLX stdlib functions for LLM to use in generation

```json
{
  "name": "get_stdlib_functions",
  "description": "Retrieve UTLX standard library functions with signatures, descriptions, and examples",
  "input_schema": {
    "type": "object",
    "properties": {
      "category": {
        "type": "string",
        "enum": ["string", "array", "object", "math", "date", "serialization", "crypto", "type", "utility"],
        "description": "Filter by function category (optional)"
      },
      "search": {
        "type": "string",
        "description": "Search term to filter functions (optional)"
      }
    }
  }
}
```

**Response Example**:
```json
{
  "functions": [
    {
      "name": "map",
      "category": "array",
      "signature": "Array<T> -> (T -> U) -> Array<U>",
      "description": "Transform each element of an array",
      "example": "$input.items |> map(item => item.price * 2)",
      "parameters": [
        {"name": "array", "type": "Array<T>"},
        {"name": "fn", "type": "Function(T) -> U"}
      ]
    },
    {
      "name": "sum",
      "category": "math",
      "signature": "Array<Number> -> Number",
      "description": "Calculate sum of numeric array",
      "example": "sum($input.items |> map(i => i.price))",
      "parameters": [
        {"name": "numbers", "type": "Array<Number>"}
      ]
    },
    {
      "name": "filter",
      "category": "array",
      "signature": "Array<T> -> (T -> Boolean) -> Array<T>",
      "description": "Filter array elements by predicate",
      "example": "$input.items |> filter(item => item.price > 10)",
      "parameters": [
        {"name": "array", "type": "Array<T>"},
        {"name": "predicate", "type": "Function(T) -> Boolean"}
      ]
    }
  ],
  "total_count": 3,
  "categories": ["array", "math"]
}
```

### Tool 3: validate_utlx

**Purpose**: Type-check and validate generated UTLX transformation

```json
{
  "name": "validate_utlx",
  "description": "Type-check UTLX transformation against input schema, return errors and warnings",
  "input_schema": {
    "type": "object",
    "properties": {
      "transformation": {
        "type": "string",
        "description": "Complete UTLX transformation code"
      },
      "input_schema": {
        "type": "object",
        "description": "Parsed input schema (from get_input_schema)",
        "optional": true
      }
    },
    "required": ["transformation"]
  }
}
```

**Response Example**:
```json
{
  "valid": true,
  "errors": [],
  "warnings": [
    {
      "severity": "warning",
      "message": "Optional field 'Customer.Email' may be null, consider using null-safe operator (?.)",
      "location": {"line": 8, "column": 15},
      "suggestion": "Use $input.Order.Customer?.Email instead"
    }
  ],
  "syntax_ok": true,
  "type_check_ok": true
}
```

### Tool 4: infer_output_schema

**Purpose**: Generate output schema from UTLX transformation and input schema

```json
{
  "name": "infer_output_schema",
  "description": "Infer the output schema (JSON Schema) from a UTLX transformation given an input schema",
  "input_schema": {
    "type": "object",
    "properties": {
      "transformation": {"type": "string"},
      "input_schema": {"type": "object"}
    },
    "required": ["transformation", "input_schema"]
  }
}
```

**Response Example**:
```json
{
  "output_schema": {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "invoiceId": {
        "type": "string",
        "description": "Derived from input.Order.@id"
      },
      "customerName": {
        "type": "string",
        "description": "Derived from input.Order.Customer.Name"
      },
      "total": {
        "type": "number",
        "description": "Sum of item prices * quantities"
      }
    },
    "required": ["invoiceId", "customerName", "total"]
  },
  "inferred_successfully": true
}
```

### Tool 5: execute_transformation

**Purpose**: Execute UTLX transformation on sample data for verification

```json
{
  "name": "execute_transformation",
  "description": "Execute UTLX transformation on sample input data and return the result",
  "input_schema": {
    "type": "object",
    "properties": {
      "transformation": {"type": "string"},
      "input_data": {"type": "string", "description": "Sample input data (XML, JSON, etc.)"},
      "input_format": {"type": "string", "enum": ["xml", "json", "csv", "yaml"]}
    },
    "required": ["transformation", "input_data", "input_format"]
  }
}
```

**Response Example**:
```json
{
  "success": true,
  "output": "{\"invoiceId\":\"ORD-001\",\"customerName\":\"Acme Corp\",\"total\":75.48}",
  "output_format": "json",
  "execution_time_ms": 23,
  "errors": []
}
```

### Tool 6: get_examples

**Purpose**: Retrieve example UTLX transformations from conformance suite

```json
{
  "name": "get_examples",
  "description": "Retrieve example UTLX transformations matching input/output format patterns",
  "input_schema": {
    "type": "object",
    "properties": {
      "input_format": {"type": "string", "enum": ["xml", "json", "csv", "yaml"]},
      "output_format": {"type": "string", "enum": ["json", "xml", "csv", "yaml"]},
      "pattern": {
        "type": "string",
        "description": "Search pattern (e.g., 'calculate total', 'map array')",
        "optional": true
      },
      "limit": {"type": "number", "default": 5}
    },
    "required": ["input_format", "output_format"]
  }
}
```

**Response Example**:
```json
{
  "examples": [
    {
      "name": "XML order to JSON invoice",
      "description": "Transform XML orders with items into JSON invoices with calculated totals",
      "input_format": "xml",
      "output_format": "json",
      "utlx": "%utlx 1.0\ninput xml\noutput json\n---\n{\n  invoiceId: $input.Order.@id,\n  total: sum($input.Order.Items.Item |> map(i => i.@price * i.@qty))\n}",
      "tags": ["calculation", "aggregation", "mapping"]
    }
  ],
  "count": 1
}
```

---

## Prompt Engineering Strategy

### System Prompt Template

```markdown
You are an expert UTLX transformation engineer. Your role is to generate valid,
type-safe UTLX transformations based on user requirements.

# Context Available via MCP Tools

1. **Input Schema**: Use get_input_schema() to understand input structure
2. **Stdlib Functions**: Use get_stdlib_functions() to find available functions
3. **Examples**: Use get_examples() to retrieve similar transformations
4. **Validation**: Use validate_utlx() to check generated code

# UTLX Syntax Rules

## Header Format
```
%utlx 1.0
input <format>    # xml, json, csv, yaml, xsd, jsch, avsc, proto
output <format>   # xml, json, csv, yaml, xsd, jsch, avsc, proto
---
```

## Functional Programming Style
- **Immutable**: All data structures are immutable
- **Pure functions**: No side effects
- **Higher-order**: map, filter, reduce, etc.
- **Type-safe**: Static type checking

## Selector Syntax
- Object properties: `$input.Order.Customer.Name`
- XML attributes: `$input.Order.@id`
- Array elements: `$input.items[0]` or `$input.items[]`
- Nested paths: `$input.Order.Items.Item`

## Common Patterns
- **Map array**: `$input.items |> map(item => {...})`
- **Filter array**: `$input.items |> filter(item => item.price > 10)`
- **Sum/aggregate**: `sum($input.items |> map(i => i.price))`
- **Conditional**: `if (condition) value1 else value2`
- **Object construction**: `{field1: value1, field2: value2}`

# Generation Workflow

1. **Understand Requirements**: Parse user prompt for input/output formats and logic
2. **Get Input Schema**: Call get_input_schema() to understand structure
3. **Find Functions**: Call get_stdlib_functions() for needed operations
4. **Check Examples**: Call get_examples() for similar patterns
5. **Generate UTLX**: Write transformation using valid syntax
6. **Validate**: Call validate_utlx() to check for errors
7. **Fix Errors**: If validation fails, refine and re-validate
8. **Return**: Provide validated UTLX with explanation

# Important Rules

- ALWAYS validate before returning
- ALWAYS use type-safe operations
- NEVER guess function signatures - use get_stdlib_functions()
- PREFER simple, readable code over clever tricks
- ADD comments for complex logic
- USE null-safe operators (?.) for optional fields

# Output Format

When generating UTLX, respond with:
1. **Explanation**: Brief description of the transformation logic
2. **UTLX Code**: Complete, validated transformation
3. **Validation Status**: Errors/warnings if any
4. **Next Steps**: Suggestions for testing or refinement
```

### Few-Shot Examples (In-Context Learning)

The system will dynamically retrieve examples using `get_examples()` based on:
- Input/output format match
- Similar functional patterns (aggregation, mapping, filtering)
- Complexity level

---

## Theia IDE Integration

### UI Mockup

```
┌─────────────────────────────────────────────────────────────────────┐
│  File: order-to-invoice.utlx          Mode: [Design-Time] [Runtime]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┬─────────────────────┬──────────┐                     │
│  │  Input   │   UTLX Editor       │  Output  │                     │
│  │  Schema  │                     │  Schema  │                     │
│  │          │  %utlx 1.0         │          │                     │
│  │  order   │  input xml         │  invoice │                     │
│  │  .xsd    │  output json       │  .schema │                     │
│  │          │  ---               │  .json   │                     │
│  │  [Load]  │  {                 │  [Export]│                     │
│  │  [Edit]  │    invoiceId: ...  │  [View]  │                     │
│  └──────────┴─────────────────────┴──────────┘                     │
│                                                                     │
│  ┌──────────────────────────── AI Assistant ────────────────────┐  │
│  │                                                               │  │
│  │  💬 Prompt:                                                   │  │
│  │  ┌─────────────────────────────────────────────────────────┐ │  │
│  │  │ Generate JSON invoice from XML order with:             │ │  │
│  │  │ - Customer name                                         │ │  │
│  │  │ - Total calculated from item prices * quantities        │ │  │
│  │  └─────────────────────────────────────────────────────────┘ │  │
│  │                                                               │  │
│  │  [Generate] [Refine] [Explain] [Examples]                    │  │
│  │                                                               │  │
│  │  🤖 Response:                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────┐ │  │
│  │  │ ✓ Generated UTLX transformation                         │ │  │
│  │  │ ✓ Validated (2 warnings)                                │ │  │
│  │  │                                                          │ │  │
│  │  │ Transformation uses:                                    │ │  │
│  │  │ • map() to iterate items                                │ │  │
│  │  │ • sum() to calculate total                              │ │  │
│  │  │                                                          │ │  │
│  │  │ ⚠ Warning: Customer.Email is optional, consider using  │ │  │
│  │  │   null-safe operator (?.)                               │ │  │
│  │  │                                                          │ │  │
│  │  │ [Insert to Editor] [Test with Sample Data]             │ │  │
│  │  └─────────────────────────────────────────────────────────┘ │  │
│  │                                                               │  │
│  │  📝 Conversation History: [3 messages]                        │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Status: AI Ready | Input: order.xsd | Output: Inferred           │
└─────────────────────────────────────────────────────────────────────┘
```

### Workflow Integration

**Mode 1: Generate from Scratch**
1. User loads input schema (XSD, JSON Schema)
2. User enters natural language prompt
3. AI generates UTLX, inserts into editor
4. User tests in Design-Time or Runtime mode

**Mode 2: Refine Existing**
1. User selects existing UTLX code
2. User asks "Optimize this" or "Add error handling"
3. AI suggests improvements
4. User accepts/rejects changes

**Mode 3: Explain Existing**
1. User opens unfamiliar UTLX file
2. User asks "Explain this transformation"
3. AI provides plain-language explanation
4. User understands logic faster

---

## Workflow Examples

### Example 1: XML to JSON Conversion

**User Input**:
- **Left Panel**: `order.xsd` (XML Schema)
- **Prompt**: "Convert XML orders to JSON invoices with customer name and total calculated from items"

**AI Process**:
1. Calls `get_input_schema("xsd", xsd_content)`
   - Discovers: `Order.@id`, `Order.Customer.Name`, `Order.Items.Item[]` with `@price`, `@quantity`
2. Calls `get_stdlib_functions("math")`
   - Finds: `sum()`, `map()`, `*` operator
3. Calls `get_examples("xml", "json", "calculate total")`
   - Retrieves similar transformation patterns
4. Generates UTLX:
```utlx
%utlx 1.0
input xml
output json
---
{
  invoiceId: $input.Order.@id,
  customerName: $input.Order.Customer.Name,
  items: $input.Order.Items.Item |> map(item => {
    description: item.@description,
    amount: item.@price * item.@quantity
  }),
  total: sum($input.Order.Items.Item |> map(item => item.@price * item.@quantity))
}
```
5. Calls `validate_utlx(transformation, input_schema)`
   - Result: ✓ Valid, 0 errors
6. Returns validated UTLX to user

**Time Saved**: 30 minutes → 2 minutes

### Example 2: Schema-to-Schema (Metadata Transformation)

**User Input**:
- **Left Panel**: `customer.xsd` (XSD schema file)
- **Prompt**: "Convert this XSD to JSON Schema, preserve all types and descriptions"

**AI Process**:
1. Calls `get_input_schema("xsd", xsd_content, "instance")`
   - Note: XSD is BOTH schema AND data (tier-2 format)
2. Calls `get_stdlib_functions("serialization")`
   - Finds: `mapTree()`, schema manipulation functions
3. Generates UTLX:
```utlx
%utlx 1.0
input xsd
output jsch
---
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": $input.schema.elements |> mapObject((elem, key) => {
    (elem.name): {
      "type": mapXsdTypeToJsonType(elem.type),
      "description": elem.annotation?.documentation
    }
  }),
  "required": $input.schema.elements
              |> filter(elem => elem.minOccurs >= 1)
              |> map(elem => elem.name)
}
```
4. Validates and returns

### Example 3: Complex Aggregation

**User Input**:
- **Left Panel**: `sales.json` (JSON data with nested transactions)
- **Prompt**: "Group sales by customer, calculate total revenue per customer, sort by revenue descending"

**AI Process**:
1. Analyzes JSON structure
2. Finds `groupBy()`, `sum()`, `sortBy()` functions
3. Generates transformation with proper aggregation logic
4. Validates against inferred schema

---

## Implementation Roadmap

### Phase 1: MCP Server Foundation (3-4 weeks)

**Week 1-2: Core Infrastructure**
- [ ] Set up TypeScript MCP server project
- [ ] Implement MCP protocol (JSON-RPC over stdio/HTTP)
- [ ] Create HTTP client to UTL-X daemon
- [ ] Implement Tool 1: `get_input_schema()`
- [ ] Implement Tool 2: `get_stdlib_functions()`
- [ ] Unit tests for each tool

**Week 3-4: Remaining Tools**
- [ ] Implement Tool 3: `validate_utlx()`
- [ ] Implement Tool 4: `infer_output_schema()`
- [ ] Implement Tool 5: `execute_transformation()`
- [ ] Implement Tool 6: `get_examples()`
- [ ] Integration tests with daemon
- [ ] Documentation for each tool

**Deliverables**:
- ✅ Standalone MCP server (TypeScript)
- ✅ 6 MCP tools fully functional
- ✅ Test suite (>80% coverage)
- ✅ API documentation

### Phase 2: LLM Integration (2-3 weeks)

**Week 1: Prompt Engineering**
- [ ] Design system prompt templates
- [ ] Create few-shot example database
- [ ] Implement prompt construction logic
- [ ] Test with Claude API

**Week 2: Validation Loop**
- [ ] Implement validation feedback loop
- [ ] Auto-retry on validation errors
- [ ] Confidence scoring for generations
- [ ] Fallback strategies

**Week 3: Multi-LLM Support**
- [ ] Abstract LLM interface
- [ ] Add OpenAI compatibility
- [ ] Add local model support (optional)
- [ ] Performance benchmarking

**Deliverables**:
- ✅ Claude API integration
- ✅ Prompt templates
- ✅ Validation feedback loop
- ✅ Multi-LLM abstraction

### Phase 3: Theia IDE Integration (2-3 weeks)

**Week 1: UI Components**
- [ ] Design AI Assistant panel mockups
- [ ] Implement panel UI (React/Vue)
- [ ] Add prompt input textarea
- [ ] Add action buttons (Generate, Refine, Explain)

**Week 2: MCP Client**
- [ ] Implement MCP client in frontend
- [ ] Connect to MCP server (WebSocket/HTTP)
- [ ] Handle streaming responses
- [ ] Error handling and retries

**Week 3: Integration & Polish**
- [ ] Integrate with existing Theia panels
- [ ] Implement "Insert to Editor" action
- [ ] Add conversation history
- [ ] UI/UX polish

**Deliverables**:
- ✅ AI Assistant panel in Theia
- ✅ MCP client
- ✅ End-to-end workflow functional
- ✅ User documentation

### Phase 4: Advanced Features (3-4 weeks)

**Week 1: Iterative Refinement**
- [ ] Multi-turn conversation support
- [ ] Context retention across turns
- [ ] Refinement suggestions
- [ ] Diff view for changes

**Week 2: Explain & Optimize**
- [ ] "Explain this UTLX" feature
- [ ] "Optimize this UTLX" feature
- [ ] Performance suggestions
- [ ] Best practice linting

**Week 3: Auto-Capture Integration**
- [ ] Mine conformance suite for examples
- [ ] Auto-add successful transformations to examples DB
- [ ] Ranking algorithm for example relevance
- [ ] Periodic retraining/refinement

**Week 4: Production Readiness**
- [ ] Performance optimization
- [ ] Security audit
- [ ] Load testing
- [ ] Deployment documentation

**Deliverables**:
- ✅ Multi-turn refinement
- ✅ Explain/Optimize features
- ✅ Auto-capture examples
- ✅ Production-ready system

---

## Security & Privacy Considerations

### Data Privacy

**Problem**: Input data/schemas may contain sensitive information

**Solutions**:
1. **On-Premise Deployment**: Host MCP server + LLM locally
2. **Data Redaction**: Auto-redact PII before sending to LLM
3. **Schema-Only Mode**: Send only schema structure, not instance data
4. **Opt-In**: User must explicitly enable AI assistance

### API Key Management

**Problem**: LLM API keys need secure storage

**Solutions**:
1. **Environment Variables**: Never hardcode API keys
2. **Secret Management**: Use Vault/AWS Secrets Manager
3. **Per-User Keys**: Allow users to bring their own API keys
4. **Rate Limiting**: Prevent abuse

### Generated Code Validation

**Problem**: LLM may generate malicious or inefficient code

**Solutions**:
1. **Sandbox Execution**: Run in isolated environment
2. **Type Checking**: Always validate before execution
3. **Performance Limits**: Timeout long-running transformations
4. **User Review**: Never auto-execute without user approval

### Compliance

**Considerations**:
- **GDPR**: Data minimization, user consent
- **HIPAA**: PHI handling for healthcare data
- **SOC 2**: Audit logging, access controls

---

## Appendix A: MCP Protocol Example

### Client → Server (Get Input Schema)

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_input_schema",
    "arguments": {
      "format": "xsd",
      "content": "<?xml version=\"1.0\"?><xs:schema>...</xs:schema>"
    }
  }
}
```

### Server → Client (Response)

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"structure\":{...},\"valid_paths\":[...],\"root_element\":\"Order\"}"
      }
    ]
  }
}
```

---

## Appendix B: Example System Prompt

See full prompt template in section "Prompt Engineering Strategy" above.

---

## Conclusion

MCP-assisted UTLX generation represents a **paradigm shift** in how users create data transformations:

1. **From Code-First to Intent-First**: Describe what you want, not how
2. **From Hours to Minutes**: Dramatically reduces time to first working transformation
3. **From Expert-Only to Accessible**: Non-programmers can create transformations
4. **From Static to Iterative**: Conversational refinement improves results
5. **From Siloed to Collaborative**: AI learns from community examples

This architecture leverages:
- ✅ Existing UTL-X infrastructure (daemon, stdlib, type system)
- ✅ Industry-standard protocols (MCP, JSON-RPC)
- ✅ Best-in-class LLMs (Claude, GPT-4)
- ✅ Privacy-preserving design (on-premise option)

**Next Steps**: Proceed to Phase 1 implementation.

---

**Document Version:** 1.0
**Last Updated:** 2025-11-02
**Related Documents**:
- theia-extension-design-with-design-time.md
- lsp-protocol-extensions.md
- stdlib function registry (utlx-functions.json)

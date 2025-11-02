# LSP Protocol Extensions for UTL-X

This document describes the custom LSP protocol extensions implemented by the UTL-X Language Server.

## Overview

The UTL-X LSP daemon supports two operational modes:

- **Design-Time Mode** (🔧): Schema-based type checking without execution
- **Runtime Mode** (▶️): Instance data transformation and execution

These modes enable different workflows:
- **Design-time** focuses on "schema engineering" - designing transformations based on schemas (XSD, JSON Schema) and inferring output schemas
- **Runtime** focuses on actual data transformation with instance data

## Custom Methods

### 1. `utlx/loadSchema`

Load an external schema (XSD or JSON Schema) to enable design-time type checking.

**Request**:
```typescript
interface LoadSchemaParams {
  uri: string;           // Document URI to associate schema with
  schemaContent: string; // Schema content (XSD or JSON Schema)
  format: "xsd" | "json-schema" | "avro";
}
```

**Response**:
```typescript
interface LoadSchemaResult {
  success: boolean;
  message?: string;
}
```

**Example**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "utlx/loadSchema",
  "params": {
    "uri": "file:///path/to/transform.utlx",
    "schemaContent": "<xs:schema>...</xs:schema>",
    "format": "xsd"
  }
}
```

**Workflow**:
1. Schema is parsed to `TypeDefinition`
2. `TypeDefinition` is converted to `TypeContext`
3. `TypeContext` is cached in `StateManager` for the document
4. Diagnostics are re-published with schema-based validation

---

### 2. `utlx/setMode`

Switch a document between design-time and runtime modes.

**Request**:
```typescript
interface SetModeParams {
  uri: string;  // Document URI
  mode: "design-time" | "runtime";
}
```

**Response**:
```typescript
interface SetModeResult {
  success: boolean;
  mode: string;
}
```

**Example**:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "utlx/setMode",
  "params": {
    "uri": "file:///path/to/transform.utlx",
    "mode": "design-time"
  }
}
```

**Behavior**:
- **Design-Time Mode**:
  - Parser diagnostics are suppressed (AST still cached for inference)
  - Schema-based path validation is active
  - Output schema inference is enabled

- **Runtime Mode**:
  - Full parser diagnostics are shown
  - Type checking based on instance data
  - Transformation execution is enabled

---

### 3. `utlx/inferOutputSchema`

Infer the output schema from a transformation in design-time mode.

**Request**:
```typescript
interface InferOutputSchemaParams {
  uri: string;                // Document URI
  pretty?: boolean;           // Pretty-print schema (default: true)
  includeComments?: boolean;  // Include comments in schema (default: true)
}
```

**Response**:
```typescript
interface InferOutputSchemaResult {
  success: boolean;
  schema?: string;  // JSON Schema (if success)
  error?: string;   // Error message (if failure)
}
```

**Example**:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "utlx/inferOutputSchema",
  "params": {
    "uri": "file:///path/to/transform.utlx",
    "pretty": true,
    "includeComments": true
  }
}
```

**Workflow**:
1. Get cached AST from document parse
2. Get input type environment (from loaded schema)
3. Use `AdvancedTypeInference.inferOutputType(ast, inputType)`
4. Generate JSON Schema from inferred type
5. Return schema as string

**Requirements**:
- Document must have been parsed successfully (AST cached)
- Schema must have been loaded via `utlx/loadSchema` (type environment exists)

**Errors**:
- "Document not found" - URI not in state manager
- "No AST cached" - Document has syntax errors or hasn't been parsed
- "No type environment" - Schema not loaded (call `utlx/loadSchema` first)
- "Failed to infer" - Type inference error (check logs)

---

## Enhanced Standard Methods

### `textDocument/hover`

Hover responses now include a mode indicator at the top:

**Design-Time Mode**:
```markdown
🔧 **Mode**: *Design-Time*

**Path**: `input.Order.Items.Item.@price`

**Type**: `number`
```

**Runtime Mode**:
```markdown
▶️ **Mode**: *Runtime*

**Path**: `input.Order.Items.Item.@price`

**Type**: `number`
```

### `textDocument/completion`

Completion logging now includes mode information for debugging:
```
DEBUG: Completion request for file:///path/to/transform.utlx at 10:25 (mode: DESIGN_TIME)
```

### `textDocument/publishDiagnostics`

Diagnostics behavior varies by mode:

**Design-Time Mode**:
- Parser diagnostics suppressed (AST still cached)
- Path-based diagnostics active (validates against schema types)

**Runtime Mode**:
- Full parser diagnostics shown
- Path-based diagnostics active (validates against instance types)

---

## Typical Workflows

### Workflow 1: Design-Time Schema Engineering

```typescript
// 1. Open document
client.sendNotification('textDocument/didOpen', {
  textDocument: {
    uri: 'file:///transform.utlx',
    languageId: 'utlx',
    version: 1,
    text: '...'
  }
});

// 2. Set to design-time mode
client.sendRequest('utlx/setMode', {
  uri: 'file:///transform.utlx',
  mode: 'design-time'
});

// 3. Load input schema
client.sendRequest('utlx/loadSchema', {
  uri: 'file:///transform.utlx',
  schemaContent: '<xs:schema>...</xs:schema>',
  format: 'xsd'
});

// 4. Edit transformation with schema-based completion/hover

// 5. Infer output schema
const result = await client.sendRequest('utlx/inferOutputSchema', {
  uri: 'file:///transform.utlx',
  pretty: true,
  includeComments: true
});

console.log(result.schema); // Generated JSON Schema
```

### Workflow 2: Runtime Data Transformation

```typescript
// 1. Open document
client.sendNotification('textDocument/didOpen', {
  textDocument: {
    uri: 'file:///transform.utlx',
    languageId: 'utlx',
    version: 1,
    text: '...'
  }
});

// 2. Set to runtime mode (default)
client.sendRequest('utlx/setMode', {
  uri: 'file:///transform.utlx',
  mode: 'runtime'
});

// 3. Type environment auto-inferred from document content
// (or loaded from instance data)

// 4. Edit transformation with instance-based completion/hover

// 5. Execute transformation (via CLI or external tool)
```

---

## State Management

The LSP daemon maintains per-document state:

| State | Description |
|-------|-------------|
| `DocumentState` | URI, text, version, cached AST |
| `TypeEnvironment` | TypeContext with input type and variable bindings |
| `Schema` | Registered schema (URI, content, format) |
| `DocumentMode` | DESIGN_TIME or RUNTIME (default: RUNTIME) |

### State Lifecycle

```
┌─────────────────┐
│ Document Opened │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Parse & Cache  │◄──── textDocument/didChange
│      AST        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Infer or Load   │◄──── utlx/loadSchema
│ Type Environment│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Set Mode       │◄──── utlx/setMode
│ (if requested)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Provide LSP    │
│   Services      │──► Completion, Hover, Diagnostics
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Infer Output    │◄──── utlx/inferOutputSchema
│   Schema        │
└─────────────────┘
```

---

## Error Handling

All custom methods follow JSON-RPC 2.0 error conventions:

```typescript
interface JsonRpcError {
  code: number;
  message: string;
  data?: any;
}
```

Common error codes:
- `-32602`: Invalid params (missing required field, wrong format)
- `-32603`: Internal error (parsing failure, type inference failure)

---

## Implementation Files

| Component | File |
|-----------|------|
| Mode Tracking | `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/state/StateManager.kt` |
| Schema Loading | `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt:handleLoadSchema()` |
| Mode Switching | `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt:handleSetMode()` |
| Output Inference | `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/schema/OutputSchemaInferenceService.kt` |
| Mode-Aware Diagnostics | `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/diagnostics/DiagnosticsPublisher.kt` |
| Mode-Aware Hover | `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/hover/HoverService.kt` |
| Schema Factory | `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/schema/SchemaTypeContextFactory.kt` |

---

## Version History

- **v1.0.0** (2025-11): Initial implementation
  - `utlx/loadSchema` method
  - `utlx/setMode` method
  - `utlx/inferOutputSchema` method
  - Mode-aware hover with indicators
  - Design-time and runtime diagnostics

---

## References

- [LSP Specification](https://microsoft.github.io/language-server-protocol/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [UTL-X Architecture Documentation](./architecture/)

# MCP Tools Reference - Quick Implementation Guide

This document provides implementation details for each of the 6 MCP tools defined in the UTL-X AI-assisted generation system.

## Tool 1: `get_input_schema`

### Purpose
Parse input data or schema files to extract structural information for the LLM.

### Parameters
```typescript
{
  source: string,        // File path or inline data
  format: "xml" | "json" | "csv" | "yaml" | "xsd" | "json-schema" | "avro" | "protobuf",
  mode?: "instance" | "schema"  // Default: auto-detect
}
```

### Returns
```typescript
{
  format: string,
  structure: {
    type: "object" | "array" | "primitive",
    properties?: Record<string, SchemaNode>,
    items?: SchemaNode,
    attributes?: Record<string, SchemaNode>,  // For XML
    required?: string[],
    examples?: any[]
  },
  summary: string  // Human-readable description for LLM
}
```

### Daemon API Calls
- **XSD schemas**: `POST /api/parse-schema` with `{ format: "xsd", content: "..." }`
- **JSON Schema**: `POST /api/parse-schema` with `{ format: "json-schema", content: "..." }`
- **Instance data**: `POST /api/infer-schema` with `{ format: "json", data: "..." }`

### Implementation Notes
- Use daemon's existing schema parsers (formats/xsd, formats/jsch)
- For instance data, infer schema from structure
- Normalize all formats to common JSON Schema-like representation
- Include type information, cardinality, and constraints
- Extract sample values from instance data

### Example Usage
```typescript
// LLM calls this tool to understand input structure
const schema = await get_input_schema({
  source: "order.xml",
  format: "xml"
});

// Returns:
{
  format: "xml",
  structure: {
    type: "object",
    properties: {
      "Order": {
        type: "object",
        attributes: {
          "id": { type: "string" }
        },
        properties: {
          "Customer": {
            type: "object",
            properties: {
              "Name": { type: "string" },
              "Email": { type: "string" }
            }
          },
          "Items": {
            type: "array",
            items: {
              type: "object",
              properties: {
                "Product": { type: "string" },
                "Quantity": { type: "integer" },
                "Price": { type: "number" }
              }
            }
          }
        }
      }
    }
  },
  summary: "XML document with Order root element containing Customer (Name, Email) and Items array"
}
```

---

## Tool 2: `get_stdlib_functions`

### Purpose
Retrieve available UTLX standard library functions, filtered by category or search terms.

### Parameters
```typescript
{
  category?: "string" | "array" | "math" | "date" | "object" | "type" | "serialization" | "crypto",
  search?: string,  // Search by name or description
  limit?: number    // Default: 20
}
```

### Returns
```typescript
{
  functions: Array<{
    name: string,
    category: string,
    signature: string,
    description: string,
    parameters: Array<{
      name: string,
      type: string,
      optional: boolean,
      description: string
    }>,
    returnType: string,
    examples?: string[]
  }>,
  total: number
}
```

### Data Source
Read from: `stdlib/build/generated/function-registry/utlx-functions.json`

### Implementation Notes
- Load and cache function registry on startup
- Support fuzzy search on name and description
- Return most relevant functions first
- Include usage examples from conformance suite
- Format signatures in UTL-X syntax

### Example Usage
```typescript
// LLM needs string manipulation functions
const funcs = await get_stdlib_functions({
  category: "string",
  limit: 10
});

// Returns:
{
  functions: [
    {
      name: "upper",
      category: "string",
      signature: "upper(str: String): String",
      description: "Converts string to uppercase",
      parameters: [
        { name: "str", type: "String", optional: false, description: "Input string" }
      ],
      returnType: "String",
      examples: ["upper(\"hello\") => \"HELLO\""]
    },
    {
      name: "replace",
      category: "string",
      signature: "replace(str: String, pattern: String, replacement: String): String",
      description: "Replaces all occurrences of pattern with replacement",
      parameters: [
        { name: "str", type: "String", optional: false, description: "Input string" },
        { name: "pattern", type: "String", optional: false, description: "Pattern to find" },
        { name: "replacement", type: "String", optional: false, description: "Replacement text" }
      ],
      returnType: "String",
      examples: ["replace(\"hello world\", \"world\", \"there\") => \"hello there\""]
    }
  ],
  total: 45
}
```

---

## Tool 3: `validate_utlx`

### Purpose
Validate UTLX transformation syntax and type safety.

### Parameters
```typescript
{
  transformation: string,  // UTLX script content
  inputSchema?: object,    // Optional input schema for type checking
  strictMode?: boolean     // Default: true
}
```

### Returns
```typescript
{
  valid: boolean,
  errors: Array<{
    line: number,
    column: number,
    severity: "error" | "warning" | "info",
    message: string,
    code?: string
  }>,
  warnings: Array<{
    line: number,
    column: number,
    message: string,
    suggestion?: string
  }>
}
```

### Daemon API Calls
- `POST /api/validate` with `{ content: "...", inputSchema: {...} }`

### Implementation Notes
- Use daemon's existing validation service
- Include both syntax and type errors
- Provide actionable error messages
- Return line/column for precise error location
- Include suggestions for common mistakes

### Example Usage
```typescript
// LLM validates generated UTLX
const result = await validate_utlx({
  transformation: `%utlx 1.0
input json
output json
---
{
  name: $input.customer.name,
  total: sum($input.items |> map(i => i.price))
}`,
  inputSchema: { /* ... */ }
});

// Returns:
{
  valid: true,
  errors: [],
  warnings: [
    {
      line: 7,
      column: 40,
      message: "Consider adding null check for optional field 'price'",
      suggestion: "Use i.price ?? 0 to provide default value"
    }
  ]
}
```

---

## Tool 4: `infer_output_schema`

### Purpose
Infer the output schema from a UTLX transformation and input schema.

### Parameters
```typescript
{
  transformation: string,
  inputSchema: object
}
```

### Returns
```typescript
{
  outputSchema: object,  // JSON Schema format
  confidence: "high" | "medium" | "low",
  uncertainFields?: string[],  // Fields with uncertain types
  summary: string
}
```

### Daemon API Calls
- `POST /api/infer-schema` with `{ transformation: "...", inputSchema: {...} }`

### Implementation Notes
- Use daemon's type inference engine (modules/analysis)
- Return JSON Schema representation
- Flag fields with uncertain types
- Handle conditional logic and dynamic keys
- Provide confidence score based on type inference certainty

### Example Usage
```typescript
// LLM checks what output schema will be produced
const result = await infer_output_schema({
  transformation: `{
    orderId: $input.Order.@id,
    customerName: $input.Order.Customer.Name,
    total: sum($input.Order.Items.Item |> map(i => i.@price * i.@quantity))
  }`,
  inputSchema: { /* ... */ }
});

// Returns:
{
  outputSchema: {
    type: "object",
    properties: {
      orderId: { type: "string" },
      customerName: { type: "string" },
      total: { type: "number" }
    },
    required: ["orderId", "customerName", "total"]
  },
  confidence: "high",
  summary: "Object with orderId (string), customerName (string), and total (number)"
}
```

---

## Tool 5: `execute_transformation`

### Purpose
Execute a UTLX transformation on sample input data to verify correctness.

### Parameters
```typescript
{
  transformation: string,
  inputData: string,      // Sample input data
  inputFormat: "xml" | "json" | "csv" | "yaml",
  outputFormat: "xml" | "json" | "csv" | "yaml"
}
```

### Returns
```typescript
{
  success: boolean,
  output?: string,        // Transformed output
  error?: {
    message: string,
    line?: number,
    column?: number,
    stackTrace?: string
  },
  executionTime: number   // Milliseconds
}
```

### Daemon API Calls
- `POST /api/execute` with `{ transformation: "...", input: "...", inputFormat: "...", outputFormat: "..." }`

### Implementation Notes
- Execute in sandboxed environment
- Set reasonable timeout (5-10 seconds)
- Capture runtime errors with context
- Return formatted output
- Track execution time for performance warnings

### Example Usage
```typescript
// LLM tests generated transformation
const result = await execute_transformation({
  transformation: `{
    name: $input.customer.name,
    email: $input.customer.email
  }`,
  inputData: '{"customer": {"name": "John", "email": "john@example.com"}}',
  inputFormat: "json",
  outputFormat: "json"
});

// Returns:
{
  success: true,
  output: '{"name": "John", "email": "john@example.com"}',
  executionTime: 12
}
```

---

## Tool 6: `get_examples`

### Purpose
Retrieve similar transformations from the conformance suite to provide as few-shot examples.

### Parameters
```typescript
{
  inputFormat: string,
  outputFormat: string,
  functions?: string[],   // Functions to prioritize
  description?: string,   // Semantic search query
  limit?: number          // Default: 5
}
```

### Returns
```typescript
{
  examples: Array<{
    name: string,
    description: string,
    transformation: string,
    input: string,
    output: string,
    relevance: number      // 0.0 - 1.0
  }>
}
```

### Data Source
Index conformance suite tests from:
- `conformance-suite/utlx/tests/**/*.yaml`

### Implementation Notes
- Build inverted index of tests on startup
- Use TF-IDF for text similarity
- Consider format compatibility (exact match preferred)
- Prioritize tests using requested functions
- Return examples ordered by relevance
- Cache index for performance

### Example Usage
```typescript
// LLM needs examples of XML to JSON transformation
const examples = await get_examples({
  inputFormat: "xml",
  outputFormat: "json",
  functions: ["map", "filter"],
  description: "transform order items to invoice",
  limit: 3
});

// Returns:
{
  examples: [
    {
      name: "xml_to_json_order_transformation",
      description: "Convert XML orders to JSON with calculated totals",
      transformation: `%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id,
  items: $input.Order.Items.Item |> map(item => {
    name: item.Name,
    total: item.@price * item.@quantity
  })
}`,
      input: "<Order id=\"123\"><Items><Item price=\"10\" quantity=\"2\">...</Item></Items></Order>",
      output: "{\"orderId\": \"123\", \"items\": [{\"name\": \"...\", \"total\": 20}]}",
      relevance: 0.89
    }
  ]
}
```

---

## Tool Error Handling

All tools should follow consistent error handling:

```typescript
// Success response
{
  success: true,
  data: { /* tool-specific response */ }
}

// Error response
{
  success: false,
  error: {
    code: "DAEMON_CONNECTION_ERROR" | "INVALID_INPUT" | "TIMEOUT" | "INTERNAL_ERROR",
    message: "Human-readable error message",
    details?: any
  }
}
```

### Common Error Codes
- `DAEMON_CONNECTION_ERROR`: Cannot connect to UTL-X daemon
- `INVALID_INPUT`: Invalid parameters provided
- `TIMEOUT`: Operation exceeded timeout
- `PARSE_ERROR`: Cannot parse input data/schema
- `VALIDATION_ERROR`: UTLX validation failed
- `EXECUTION_ERROR`: Runtime error during transformation
- `INTERNAL_ERROR`: Unexpected server error

---

## Performance Targets

| Tool | Target Response Time | Notes |
|------|---------------------|-------|
| `get_input_schema` | < 500ms | May be slower for large XSD files |
| `get_stdlib_functions` | < 100ms | Should be cached |
| `validate_utlx` | < 300ms | Parser + type checker |
| `infer_output_schema` | < 500ms | Complex type inference |
| `execute_transformation` | < 1000ms | Depends on transformation complexity |
| `get_examples` | < 200ms | With pre-built index |

---

## Testing Strategy

### Unit Tests
- Test each tool with valid inputs
- Test error handling for invalid inputs
- Test edge cases (empty data, malformed schemas)
- Mock daemon responses

### Integration Tests
- Test with real daemon connection
- Test with real conformance suite data
- Test concurrent requests
- Test timeout scenarios

### End-to-End Tests
- Test complete generation workflow
- Test multi-turn conversations
- Test error recovery
- Test with various input formats

---

## Configuration

### Environment Variables
```bash
# Daemon connection
UTLX_DAEMON_URL=http://localhost:7778
UTLX_DAEMON_TIMEOUT=10000  # milliseconds

# MCP Server
MCP_PORT=7779
MCP_LOG_LEVEL=info

# Function registry
FUNCTION_REGISTRY_PATH=./stdlib/build/generated/function-registry/utlx-functions.json

# Conformance suite
CONFORMANCE_SUITE_PATH=./conformance-suite/utlx/tests
```

### Config File (~/.utlx/mcp-config.yaml)
```yaml
daemon:
  url: http://localhost:7778
  timeout: 10000
  retry_attempts: 3

server:
  port: 7779
  log_level: info

tools:
  get_examples:
    index_path: ~/.utlx/conformance-index.json
    rebuild_on_startup: false

  execute_transformation:
    timeout: 5000
    max_memory_mb: 100
```

---

## Next Steps

1. Implement basic MCP server skeleton
2. Implement each tool incrementally
3. Test with mock daemon responses
4. Integrate with real daemon
5. Build example index
6. Optimize performance
7. Add comprehensive error handling
8. Write integration tests

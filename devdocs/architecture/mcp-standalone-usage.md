# MCP Server - Standalone Usage Without LLM Agent

**Version:** 1.0
**Date:** 2025-11-03
**Status:** Usage Guide

---

## Executive Summary

### Question
**Can we use the MCP server without an LLM agent connected?**

### Answer
**YES - Absolutely!** The MCP server provides valuable capabilities even without LLM-based generation.

### Key Insight
The MCP server is **NOT just for AI generation**. It's a comprehensive **UTL-X transformation toolkit** with 8 powerful tools, most of which work independently of LLM.

---

## Usage Modes

### Mode 1: Standalone API Server (No LLM Required) ✅

**Use Case**: Direct tool invocation via API

```
Client (curl/Postman/code) → MCP Server → Daemon
                                ↓
                           Tool Results
```

**Available Without LLM**:
- ✅ Tool 1: `get_input_schema` - Parse schemas/data
- ✅ Tool 2: `get_stdlib_functions` - Browse function library
- ✅ Tool 3: `validate_utlx` - Validate transformations
- ✅ Tool 4: `infer_output_schema` - Type inference
- ✅ Tool 5: `execute_transformation` - Run transformations
- ✅ Tool 6: `get_examples` - Search conformance suite
- ✅ Tool 7: `analyze_schema_compatibility` - Schema analysis
- ⚠️ Tool 8: `generate_transformation_variants` - Requires LLM

**Only Requires LLM**:
- ❌ AI-powered UTLX generation from natural language prompts

---

### Mode 2: LLM-Assisted Mode (Optional Enhancement) 🤖

**Use Case**: Natural language → UTLX generation

```
User Prompt → MCP Server → LLM → Generated UTLX
                    ↓
              MCP Tools provide context
```

**This is OPTIONAL** - You can add it later when ready!

---

## Standalone Capabilities (No LLM Needed)

### 1. Schema Analysis API

**Capability**: Analyze compatibility between input/output schemas

**Example**:
```bash
curl -X POST http://localhost:7779/api/v1/analyze/compatibility \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "my-company",
    "inputSchema": "'$(cat order.xsd)'",
    "inputFormat": "xsd",
    "outputSchema": "'$(cat invoice.xsd)'",
    "outputFormat": "xsd"
  }'
```

**Response**:
```json
{
  "compatible": false,
  "coverage": {
    "fullCoverage": [
      {
        "outputField": "Invoice.InvoiceNumber",
        "inputField": "Order.OrderID",
        "mappingType": "direct",
        "confidence": 1.0
      },
      {
        "outputField": "Invoice.Total",
        "inputField": "Order.Items.Item",
        "mappingType": "aggregated",
        "transformation": "sum(Items.Item |> map(i => i.Quantity * i.Price))"
      }
    ],
    "missingRequired": [
      {
        "outputField": "Invoice.TaxID",
        "required": true,
        "suggestions": [
          "Add constant value",
          "Use default from config"
        ]
      }
    ]
  },
  "mappingComplexity": "moderate",
  "confidence": 0.85
}
```

**Value**: Understand mapping feasibility before writing code!

---

### 2. Schema Parsing API

**Capability**: Extract structure from XSD, JSON Schema, Avro, etc.

**Example**:
```bash
curl -X POST http://localhost:7779/api/v1/schemas/parse \
  -d '{
    "source": "'$(cat customer.xsd)'",
    "format": "xsd"
  }'
```

**Response**:
```json
{
  "format": "xsd",
  "structure": {
    "type": "object",
    "properties": {
      "Customer": {
        "type": "object",
        "properties": {
          "CustomerID": { "type": "string", "required": true },
          "Name": { "type": "string", "required": true },
          "Email": { "type": "string", "required": false }
        }
      }
    }
  },
  "summary": "XSD schema with Customer element (CustomerID, Name, Email)"
}
```

**Value**: Automated schema documentation and analysis!

---

### 3. UTLX Validation API

**Capability**: Validate syntax and type safety of UTLX code

**Example**:
```bash
curl -X POST http://localhost:7779/api/v1/transformations/validate \
  -d '{
    "transformation": "%utlx 1.0\ninput xml\noutput json\n---\n{name: $input.Customer.Name}",
    "inputSchema": { ... }
  }'
```

**Response**:
```json
{
  "valid": true,
  "errors": [],
  "warnings": [
    {
      "line": 5,
      "message": "Field 'Name' may be null",
      "suggestion": "Use null-safe operator: $input.Customer.Name ?? \"Unknown\""
    }
  ]
}
```

**Value**: CI/CD integration for UTLX validation!

---

### 4. UTLX Execution API

**Capability**: Execute transformations on sample data

**Example**:
```bash
curl -X POST http://localhost:7779/api/v1/transformations/execute \
  -d '{
    "transformation": "%utlx 1.0\ninput xml\noutput json\n---\n{name: $input.Customer.Name}",
    "inputData": "<Customer><Name>John</Name></Customer>",
    "inputFormat": "xml",
    "outputFormat": "json"
  }'
```

**Response**:
```json
{
  "success": true,
  "output": "{\"name\": \"John\"}",
  "executionTime": 12
}
```

**Value**: Testing and debugging transformations!

---

### 5. Function Discovery API

**Capability**: Browse 400+ stdlib functions

**Example**:
```bash
curl -X POST http://localhost:7779/api/v1/functions/search \
  -d '{
    "category": "string",
    "limit": 10
  }'
```

**Response**:
```json
{
  "functions": [
    {
      "name": "upper",
      "category": "string",
      "signature": "upper(str: String): String",
      "description": "Converts string to uppercase",
      "examples": ["upper(\"hello\") => \"HELLO\""]
    },
    {
      "name": "replace",
      "category": "string",
      "signature": "replace(str: String, pattern: String, replacement: String): String",
      "description": "Replaces occurrences of pattern"
    }
  ]
}
```

**Value**: Function library browser, documentation tool!

---

### 6. Example Search API

**Capability**: Find similar transformations from conformance suite

**Example**:
```bash
curl -X POST http://localhost:7779/api/v1/examples/search \
  -d '{
    "inputFormat": "xml",
    "outputFormat": "json",
    "functions": ["map", "filter"],
    "limit": 5
  }'
```

**Response**:
```json
{
  "examples": [
    {
      "name": "xml_to_json_order_transformation",
      "description": "Convert XML orders to JSON",
      "transformation": "%utlx 1.0\ninput xml\noutput json\n---\n...",
      "relevance": 0.89
    }
  ]
}
```

**Value**: Learning tool, code examples repository!

---

### 7. Type Inference API

**Capability**: Infer output schema from UTLX + input schema

**Example**:
```bash
curl -X POST http://localhost:7779/api/v1/schemas/infer-output \
  -d '{
    "transformation": "%utlx 1.0\n...\n{name: $input.Customer.Name, total: sum(...)}",
    "inputSchema": { ... }
  }'
```

**Response**:
```json
{
  "outputSchema": {
    "type": "object",
    "properties": {
      "name": { "type": "string" },
      "total": { "type": "number" }
    }
  },
  "confidence": "high"
}
```

**Value**: Generate output schemas automatically!

---

## Practical Use Cases (No LLM Required)

### Use Case 1: Schema Analysis Tool

**Scenario**: Developer has `source.xsd` and `target.xsd`, needs to know if mapping is possible

**Workflow**:
```bash
# 1. Analyze compatibility
analysis=$(curl -s http://localhost:7779/api/v1/analyze/compatibility \
  -d @compatibility-request.json)

# 2. Get coverage report
echo "$analysis" | jq '.coverage'

# 3. Identify gaps
echo "$analysis" | jq '.coverage.missingRequired'
```

**Output**:
```json
{
  "compatible": false,
  "coverage": {
    "fullCoverage": [...],
    "missingRequired": [
      {
        "outputField": "Invoice.TaxID",
        "suggestions": ["Add constant", "Use external lookup"]
      }
    ]
  }
}
```

**Value**: Know before you code!

---

### Use Case 2: UTLX CI/CD Validation

**Scenario**: Validate all UTLX files in repository on every commit

**GitHub Actions Workflow**:
```yaml
name: Validate UTLX Transformations

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Start MCP Server
        run: |
          docker run -d -p 7779:7779 utlx-mcp-server

      - name: Validate all UTLX files
        run: |
          for file in transformations/**/*.utlx; do
            echo "Validating $file..."
            curl -X POST http://localhost:7779/api/v1/transformations/validate \
              -d @<(cat <<EOF
              {
                "transformation": "$(cat $file)"
              }
              EOF
              ) | jq -e '.valid == true' || exit 1
          done
```

**Value**: Catch UTLX errors before deployment!

---

### Use Case 3: Transformation Testing Framework

**Scenario**: Test UTLX transformations against sample data

**Test Suite**:
```typescript
// test-transformations.ts
import { MCPClient } from './mcp-client';

describe('Order to Invoice Transformation', () => {
  const mcp = new MCPClient('http://localhost:7779');

  it('should transform basic order', async () => {
    const result = await mcp.execute({
      transformation: readFile('order-to-invoice.utlx'),
      inputData: readFile('test-data/order-001.xml'),
      inputFormat: 'xml',
      outputFormat: 'json'
    });

    expect(result.success).toBe(true);
    expect(JSON.parse(result.output)).toMatchObject({
      invoiceNumber: 'INV-001',
      total: 150.00
    });
  });

  it('should handle missing optional fields', async () => {
    const result = await mcp.execute({
      transformation: readFile('order-to-invoice.utlx'),
      inputData: '<Order><Customer></Customer></Order>',
      inputFormat: 'xml',
      outputFormat: 'json'
    });

    expect(result.success).toBe(true);
  });
});
```

**Value**: Automated testing for transformations!

---

### Use Case 4: Schema Documentation Generator

**Scenario**: Generate documentation from XSD/JSON Schema files

**Script**:
```bash
#!/bin/bash
# generate-schema-docs.sh

for schema in schemas/*.xsd; do
  echo "Parsing $schema..."

  # Parse schema
  result=$(curl -s http://localhost:7779/api/v1/schemas/parse \
    -d @<(cat <<EOF
    {
      "source": "$(cat $schema)",
      "format": "xsd"
    }
    EOF
    ))

  # Extract structure
  summary=$(echo "$result" | jq -r '.summary')
  properties=$(echo "$result" | jq '.structure.properties')

  # Generate Markdown
  cat >> SCHEMAS.md <<EOF
## $(basename $schema)

**Description**: $summary

**Fields**:
$(echo "$properties" | jq -r 'to_entries[] | "- **\(.key)**: \(.value.type)"')

EOF
done
```

**Value**: Automatic schema documentation!

---

### Use Case 5: Function Library Browser

**Scenario**: Interactive tool to browse UTL-X functions

**CLI Tool**:
```typescript
// utlx-functions.ts
import { MCPClient } from './mcp-client';
import { program } from 'commander';

program
  .command('search <category>')
  .action(async (category) => {
    const mcp = new MCPClient('http://localhost:7779');

    const functions = await mcp.call('get_stdlib_functions', {
      category,
      limit: 20
    });

    console.log(`\n${category.toUpperCase()} Functions:\n`);
    functions.forEach(fn => {
      console.log(`  ${fn.name}${fn.signature}`);
      console.log(`    ${fn.description}`);
      if (fn.examples) {
        console.log(`    Example: ${fn.examples[0]}`);
      }
      console.log('');
    });
  });

program.parse();
```

**Usage**:
```bash
$ node utlx-functions.js search string

STRING Functions:

  upper(str: String): String
    Converts string to uppercase
    Example: upper("hello") => "HELLO"

  replace(str: String, pattern: String, replacement: String): String
    Replaces all occurrences of pattern
    Example: replace("hello world", "world", "there") => "hello there"
```

**Value**: Easy function discovery!

---

### Use Case 6: Visual Schema Mapper (Web UI)

**Scenario**: Simple web UI for schema mapping (without Theia)

**HTML/JS Application**:
```html
<!DOCTYPE html>
<html>
<head>
  <title>UTL-X Schema Mapper</title>
</head>
<body>
  <h1>Schema Compatibility Analyzer</h1>

  <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
    <div>
      <h2>Input Schema</h2>
      <textarea id="inputSchema" rows="20" style="width: 100%;"></textarea>
      <select id="inputFormat">
        <option value="xsd">XSD</option>
        <option value="json-schema">JSON Schema</option>
      </select>
    </div>

    <div>
      <h2>Output Schema</h2>
      <textarea id="outputSchema" rows="20" style="width: 100%;"></textarea>
      <select id="outputFormat">
        <option value="xsd">XSD</option>
        <option value="json-schema">JSON Schema</option>
      </select>
    </div>
  </div>

  <button onclick="analyzeCompatibility()" style="margin: 20px 0; padding: 10px 20px;">
    Analyze Compatibility
  </button>

  <div id="results"></div>

  <script>
    async function analyzeCompatibility() {
      const response = await fetch('http://localhost:7779/api/v1/analyze/compatibility', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tenantId: 'demo',
          inputSchema: document.getElementById('inputSchema').value,
          inputFormat: document.getElementById('inputFormat').value,
          outputSchema: document.getElementById('outputSchema').value,
          outputFormat: document.getElementById('outputFormat').value
        })
      });

      const result = await response.json();

      // Display results
      const resultsDiv = document.getElementById('results');
      resultsDiv.innerHTML = `
        <h2>Analysis Results</h2>
        <p><strong>Compatible:</strong> ${result.compatible ? 'Yes ✅' : 'No ❌'}</p>
        <p><strong>Coverage:</strong> ${Math.round(result.confidence * 100)}%</p>

        <h3>Direct Mappings (${result.coverage.fullCoverage.length})</h3>
        <ul>
          ${result.coverage.fullCoverage.map(m =>
            `<li>${m.outputField} ← ${m.inputField} (${m.mappingType})</li>`
          ).join('')}
        </ul>

        ${result.coverage.missingRequired.length > 0 ? `
          <h3>Missing Required Fields (${result.coverage.missingRequired.length})</h3>
          <ul>
            ${result.coverage.missingRequired.map(m =>
              `<li>${m.outputField} - ${m.suggestions.join(', ')}</li>`
            ).join('')}
          </ul>
        ` : ''}
      `;
    }
  </script>
</body>
</html>
```

**Value**: Quick schema analysis without full IDE!

---

## MCP Server Configuration (Standalone Mode)

### Disable LLM Features

**config/standalone.yaml**:
```yaml
server:
  port: 7779
  log_level: info

daemon:
  url: http://localhost:7778
  timeout: 10000

# LLM features DISABLED
llm:
  enabled: false  # ← No LLM required!

tools:
  # These work without LLM
  get_input_schema:
    enabled: true
  get_stdlib_functions:
    enabled: true
  validate_utlx:
    enabled: true
  infer_output_schema:
    enabled: true
  execute_transformation:
    enabled: true
  get_examples:
    enabled: true
  analyze_schema_compatibility:
    enabled: true

  # This requires LLM
  generate_transformation_variants:
    enabled: false  # Disabled in standalone mode
```

---

## REST API Endpoints (Standalone)

### Available Without LLM

```
POST /api/v1/schemas/parse
POST /api/v1/schemas/infer-output
POST /api/v1/analyze/compatibility
POST /api/v1/transformations/validate
POST /api/v1/transformations/execute
POST /api/v1/functions/search
POST /api/v1/examples/search
GET  /api/v1/health
GET  /api/v1/metrics
```

### Requires LLM (Can Be Added Later)

```
POST /api/v1/transformations/generate  # AI generation
POST /api/v1/transformations/variants  # AI optimization
POST /api/v1/chat                      # Conversational interface
```

---

## Deployment (Standalone Mode)

### Docker Compose (No LLM)

```yaml
version: '3.8'

services:
  utlx-daemon:
    image: utlx-daemon:latest
    ports:
      - "7778:7778"

  mcp-server:
    image: utlx-mcp-server:latest
    ports:
      - "7779:7779"
    environment:
      - DAEMON_URL=http://utlx-daemon:7778
      - LLM_ENABLED=false  # Standalone mode
    depends_on:
      - utlx-daemon
```

**Start**:
```bash
docker-compose up -d

# Test
curl http://localhost:7779/health
```

---

## Adding LLM Later (Incremental Enhancement)

### Phase 1: Standalone MCP Server (Weeks 1-7)
- ✅ All 7 non-LLM tools working
- ✅ REST APIs available
- ✅ Can be used for analysis, validation, testing

### Phase 2: Add LLM (Weeks 8-10) - OPTIONAL
```yaml
# Update config to enable LLM
llm:
  enabled: true
  provider: claude
  api_key: ${ANTHROPIC_API_KEY}
```

**New capabilities unlocked**:
- AI-powered generation from prompts
- Transformation variants
- Conversational interface

---

## Summary

### Can You Use MCP Server Without LLM? YES! ✅

**7 out of 8 tools work standalone**:
1. ✅ Schema parsing
2. ✅ Function discovery
3. ✅ UTLX validation
4. ✅ Type inference
5. ✅ Transformation execution
6. ✅ Example search
7. ✅ Schema compatibility analysis
8. ❌ AI generation (requires LLM)

### Value Proposition (Standalone)

Even without LLM, MCP Server is a **powerful UTL-X toolkit**:
- **Schema analysis** - Understand mapping feasibility
- **CI/CD integration** - Validate transformations
- **Testing framework** - Execute and verify
- **Documentation** - Auto-generate from schemas
- **Function library** - Browse 400+ functions
- **Example search** - Find similar transformations

### Incremental Adoption Path

```
Week 1-4:  Build MCP Server (7 standalone tools)
Week 5-7:  Deploy and use standalone
           ↓
           Use for: analysis, validation, testing, docs
           ↓
Week 8+:   Add LLM when ready (optional enhancement)
           ↓
           Unlock: AI generation, variants, chat
```

**Bottom Line**: MCP Server is valuable **immediately**, and LLM makes it **even better** later!

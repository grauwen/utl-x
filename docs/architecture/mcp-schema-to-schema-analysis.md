# MCP Schema-to-Schema Transformation Analysis

**Version:** 1.0
**Date:** 2025-11-03
**Status:** Architecture Extension
**Parent Document:** [mcp-assisted-generation.md](./mcp-assisted-generation.md)

---

## Table of Contents

1. [Overview](#overview)
2. [Use Case: Schema-to-Schema Mapping](#use-case-schema-to-schema-mapping)
3. [New MCP Tools](#new-mcp-tools)
4. [Analysis Workflows](#analysis-workflows)
5. [Daemon API Extensions](#daemon-api-extensions)
6. [UI Integration](#ui-integration)
7. [Implementation Details](#implementation-details)

---

## Overview

### Problem Statement

When users provide **both input and output schemas** (e.g., `myinput.xsd` and `myoutput.xsd`), they need to:

1. **Analyze Coverage**: Can the input schema fully satisfy the output schema requirements?
2. **Identify Gaps**: What output fields cannot be derived from input?
3. **Generate Transformation**: Create UTLX that maps input → output
4. **Optimize**: Suggest multiple transformation strategies with performance trade-offs

### Current vs. Enhanced Workflow

**Current (data-driven):**
```
User provides: input data + prompt
→ AI generates UTLX based on data structure
```

**Enhanced (schema-driven):**
```
User provides: input schema (XSD) + output schema (XSD)
→ AI analyzes compatibility
→ AI reports coverage gaps
→ AI generates UTLX with multiple optimization options
```

---

## Use Case: Schema-to-Schema Mapping

### Example Scenario

**Input Schema** (`order.xsd`):
```xml
<xs:schema>
  <xs:complexType name="Order">
    <xs:sequence>
      <xs:element name="OrderID" type="xs:string"/>
      <xs:element name="OrderDate" type="xs:date"/>
      <xs:element name="Customer">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="CustomerID" type="xs:string"/>
            <xs:element name="Name" type="xs:string"/>
            <xs:element name="Email" type="xs:string" minOccurs="0"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
      <xs:element name="Items">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="Item" maxOccurs="unbounded">
              <xs:complexType>
                <xs:sequence>
                  <xs:element name="ProductID" type="xs:string"/>
                  <xs:element name="Quantity" type="xs:integer"/>
                  <xs:element name="UnitPrice" type="xs:decimal"/>
                </xs:sequence>
              </xs:complexType>
            </xs:element>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

**Output Schema** (`invoice.xsd`):
```xml
<xs:schema>
  <xs:complexType name="Invoice">
    <xs:sequence>
      <xs:element name="InvoiceNumber" type="xs:string"/>
      <xs:element name="InvoiceDate" type="xs:date"/>
      <xs:element name="CustomerName" type="xs:string"/>
      <xs:element name="CustomerEmail" type="xs:string"/>
      <xs:element name="TaxID" type="xs:string"/>  <!-- NOT in input! -->
      <xs:element name="LineItems">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="LineItem" maxOccurs="unbounded">
              <xs:complexType>
                <xs:sequence>
                  <xs:element name="Description" type="xs:string"/>
                  <xs:element name="Amount" type="xs:decimal"/>
                </xs:sequence>
              </xs:complexType>
            </xs:element>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
      <xs:element name="Subtotal" type="xs:decimal"/>
      <xs:element name="Tax" type="xs:decimal"/>
      <xs:element name="Total" type="xs:decimal"/>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

### Desired Workflow

1. **User loads schemas**: Drag `order.xsd` into input panel, `invoice.xsd` into output panel
2. **User clicks "Analyze Mapping"**
3. **AI performs coverage analysis**:
   - ✅ InvoiceNumber ← OrderID (direct mapping)
   - ✅ InvoiceDate ← OrderDate (direct mapping)
   - ✅ CustomerName ← Customer.Name (direct mapping)
   - ✅ CustomerEmail ← Customer.Email (direct mapping, optional)
   - ❌ **TaxID** - NOT AVAILABLE in input schema
   - ✅ LineItems.Description ← Items.Item.ProductID (mapping available)
   - ✅ LineItems.Amount ← Items.Item.Quantity * Items.Item.UnitPrice (calculated)
   - ✅ Subtotal ← sum(LineItems.Amount) (aggregation)
   - ⚠️ **Tax** - NOT AVAILABLE, requires external data or constant
   - ⚠️ **Total** - CAN BE CALCULATED (Subtotal + Tax, but Tax is missing)

4. **AI presents analysis report**:
   ```
   COVERAGE ANALYSIS

   ✅ Full Coverage (6 fields):
   - InvoiceNumber ← Order.OrderID
   - InvoiceDate ← Order.OrderDate
   - CustomerName ← Order.Customer.Name
   - CustomerEmail ← Order.Customer.Email (optional)
   - LineItems.Description ← Order.Items.Item.ProductID
   - LineItems.Amount ← Order.Items.Item.Quantity * Order.Items.Item.UnitPrice
   - Subtotal ← sum(Order.Items.Item amounts)

   ❌ Missing Required Fields (1 field):
   - TaxID: Required in output, not available in input
     Suggestion: Add constant value or prompt for external source

   ⚠️ Partial Coverage (2 fields):
   - Tax: Not in input, needed for Total calculation
     Suggestion: Apply fixed rate (e.g., 10%) or prompt user
   - Total: Can calculate IF Tax is provided

   RECOMMENDATION:
   1. Add constant TaxID: "UNKNOWN" or prompt user for value
   2. Add tax calculation: Subtotal * 0.10 (configurable rate)
   3. Generate UTLX with placeholders for user to fill
   ```

5. **User chooses action**:
   - "Generate with placeholders" → AI creates UTLX with `/* TODO: Provide TaxID */`
   - "Use defaults" → AI generates with `taxID: "UNKNOWN"`, `tax: subtotal * 0.10`
   - "Skip missing fields" → AI generates without TaxID (validation warning)

---

## New MCP Tools

### Tool 7: `analyze_schema_compatibility`

**Purpose**: Analyze whether input schema can satisfy output schema requirements.

**Parameters**:
```typescript
{
  inputSchema: string,      // File path or inline XSD/JSON Schema/Avro/Protobuf
  inputFormat: "xsd" | "json-schema" | "avro" | "protobuf",
  outputSchema: string,     // File path or inline
  outputFormat: "xsd" | "json-schema" | "avro" | "protobuf"
}
```

**Returns**:
```typescript
{
  compatible: boolean,      // Can input fully satisfy output?
  coverage: {
    fullCoverage: Array<{
      outputField: string,
      inputField: string,
      mappingType: "direct" | "calculated" | "aggregated",
      transformation?: string
    }>,
    missingRequired: Array<{
      outputField: string,
      required: boolean,
      suggestions: string[]
    }>,
    partialCoverage: Array<{
      outputField: string,
      issue: string,
      suggestions: string[]
    }>
  },
  mappingComplexity: "simple" | "moderate" | "complex",
  estimatedGenerationConfidence: number  // 0.0 - 1.0
}
```

**Daemon API Call**:
```
POST /api/analyze-compatibility
{
  inputSchema: { format: "xsd", content: "..." },
  outputSchema: { format: "xsd", content: "..." }
}
```

**Example Usage**:
```typescript
const analysis = await analyze_schema_compatibility({
  inputSchema: "order.xsd",
  inputFormat: "xsd",
  outputSchema: "invoice.xsd",
  outputFormat: "xsd"
});

// Returns:
{
  compatible: false,  // Missing required TaxID
  coverage: {
    fullCoverage: [
      {
        outputField: "Invoice.InvoiceNumber",
        inputField: "Order.OrderID",
        mappingType: "direct"
      },
      {
        outputField: "Invoice.Subtotal",
        inputField: "Order.Items.Item",
        mappingType: "aggregated",
        transformation: "sum(Items.Item |> map(i => i.Quantity * i.UnitPrice))"
      }
    ],
    missingRequired: [
      {
        outputField: "Invoice.TaxID",
        required: true,
        suggestions: [
          "Add constant value: \"UNKNOWN\"",
          "Prompt user for value",
          "Use default from configuration"
        ]
      }
    ],
    partialCoverage: [
      {
        outputField: "Invoice.Tax",
        issue: "Not in input schema, needed for Total calculation",
        suggestions: [
          "Calculate as Subtotal * 0.10 (10% tax rate)",
          "Prompt user for tax rate",
          "Leave as placeholder for manual entry"
        ]
      }
    ]
  },
  mappingComplexity: "moderate",
  estimatedGenerationConfidence: 0.75
}
```

---

### Tool 8: `generate_transformation_variants`

**Purpose**: Generate multiple UTLX transformations with different optimization strategies.

**Parameters**:
```typescript
{
  inputSchema: string,
  outputSchema: string,
  optimizationGoal?: "speed" | "memory" | "readability" | "balanced",
  maxVariants?: number  // Default: 3
}
```

**Returns**:
```typescript
{
  variants: Array<{
    transformation: string,   // UTLX code
    strategy: string,         // Description of optimization approach
    estimatedPerformance: {
      speed: "fast" | "medium" | "slow",
      memoryUsage: "low" | "medium" | "high",
      readability: number     // 1-10 scale
    },
    tradeoffs: string[]
  }>
}
```

**Example Usage**:
```typescript
const variants = await generate_transformation_variants({
  inputSchema: "order.xsd",
  outputSchema: "invoice.xsd",
  optimizationGoal: "balanced",
  maxVariants: 3
});

// Returns:
{
  variants: [
    {
      transformation: `
%utlx 1.0
input xml
output xml
---
let items = $input.Order.Items.Item
let lineItems = items |> map(item => {
  description: item.ProductID,
  amount: item.Quantity * item.UnitPrice
})
let subtotal = sum(lineItems |> map(li => li.amount))

{
  Invoice: {
    InvoiceNumber: $input.Order.OrderID,
    InvoiceDate: $input.Order.OrderDate,
    CustomerName: $input.Order.Customer.Name,
    CustomerEmail: $input.Order.Customer.Email ?? "no-email@example.com",
    TaxID: "UNKNOWN",  /* TODO: Provide TaxID */
    LineItems: {
      LineItem: lineItems
    },
    Subtotal: subtotal,
    Tax: subtotal * 0.10,
    Total: subtotal * 1.10
  }
}`,
      strategy: "Balanced: Readable with good performance",
      estimatedPerformance: {
        speed: "medium",
        memoryUsage: "medium",
        readability: 8
      },
      tradeoffs: [
        "Uses let bindings for clarity",
        "Iterates items twice (once for lineItems, once for subtotal)",
        "Easy to understand and modify"
      ]
    },
    {
      transformation: `
%utlx 1.0
input xml
output xml
---
let itemsWithAmounts = $input.Order.Items.Item |> map(item => {
  description: item.ProductID,
  amount: item.Quantity * item.UnitPrice
})
let subtotal = sum(itemsWithAmounts |> map(li => li.amount))

{
  Invoice: {
    InvoiceNumber: $input.Order.OrderID,
    InvoiceDate: $input.Order.OrderDate,
    CustomerName: $input.Order.Customer.Name,
    CustomerEmail: $input.Order.Customer.Email ?? "no-email@example.com",
    TaxID: "UNKNOWN",
    LineItems: { LineItem: itemsWithAmounts },
    Subtotal: subtotal,
    Tax: subtotal * 0.10,
    Total: subtotal * 1.10
  }
}`,
      strategy: "Optimized for Speed: Single pass through items",
      estimatedPerformance: {
        speed: "fast",
        memoryUsage: "medium",
        readability: 7
      },
      tradeoffs: [
        "Stores items with amounts in memory (itemsWithAmounts)",
        "Single iteration through items",
        "Slightly less readable due to combined operations"
      ]
    },
    {
      transformation: `
%utlx 1.0
input xml
output xml
---
{
  Invoice: {
    InvoiceNumber: $input.Order.OrderID,
    InvoiceDate: $input.Order.OrderDate,
    CustomerName: $input.Order.Customer.Name,
    CustomerEmail: $input.Order.Customer.Email ?? "no-email@example.com",
    TaxID: "UNKNOWN",
    LineItems: {
      LineItem: $input.Order.Items.Item |> map(item => {
        description: item.ProductID,
        amount: item.Quantity * item.UnitPrice
      })
    },
    Subtotal: sum($input.Order.Items.Item |> map(i => i.Quantity * i.UnitPrice)),
    Tax: sum($input.Order.Items.Item |> map(i => i.Quantity * i.UnitPrice)) * 0.10,
    Total: sum($input.Order.Items.Item |> map(i => i.Quantity * i.UnitPrice)) * 1.10
  }
}`,
      strategy: "Optimized for Memory: No intermediate variables",
      estimatedPerformance: {
        speed: "slow",
        memoryUsage: "low",
        readability: 5
      },
      tradeoffs: [
        "No intermediate storage - minimal memory footprint",
        "Iterates items 4 times (LineItems, Subtotal, Tax, Total)",
        "Harder to read and maintain",
        "Consider if input data is extremely large"
      ]
    }
  ]
}
```

---

## Analysis Workflows

### Workflow 1: Automatic Coverage Analysis

```
User Action: Loads input.xsd and output.xsd
↓
AI automatically calls: analyze_schema_compatibility()
↓
AI presents coverage report in UI:
  ┌─────────────────────────────────────────┐
  │ SCHEMA COMPATIBILITY ANALYSIS           │
  ├─────────────────────────────────────────┤
  │ ✅ Compatible: 85%                      │
  │                                         │
  │ Direct Mappings: 6 fields               │
  │ Calculated Fields: 3 fields             │
  │ Missing Required: 1 field               │
  │ Missing Optional: 0 fields              │
  │                                         │
  │ [View Details] [Generate UTLX]          │
  └─────────────────────────────────────────┘
```

### Workflow 2: Interactive Gap Resolution

```
AI: "I found 1 missing required field: Invoice.TaxID"
AI: "How would you like to handle this?"

Options presented:
1. Use constant value: [_________]
2. Use expression: ${___________}
3. Skip field (validation error)
4. Mark as TODO for manual completion

User selects: "Use constant value" → enters "UNKNOWN"
↓
AI regenerates UTLX with: TaxID: "UNKNOWN"
```

### Workflow 3: Optimization Selection

```
AI: "I've generated 3 transformation variants:"

┌──────────────────────────────────────────────────────┐
│ Variant 1: BALANCED (Recommended)                    │
│ • Speed: Medium | Memory: Medium | Readability: 8/10 │
│ [Preview Code] [Select]                              │
├──────────────────────────────────────────────────────┤
│ Variant 2: SPEED OPTIMIZED                           │
│ • Speed: Fast | Memory: Medium | Readability: 7/10   │
│ [Preview Code] [Select]                              │
├──────────────────────────────────────────────────────┤
│ Variant 3: MEMORY OPTIMIZED                          │
│ • Speed: Slow | Memory: Low | Readability: 5/10      │
│ [Preview Code] [Select]                              │
└──────────────────────────────────────────────────────┘

User clicks "Preview Code" → Shows side-by-side diff
User clicks "Select" → Inserts chosen variant into editor
```

---

## Daemon API Extensions

### New Endpoint: `/api/analyze-compatibility`

**Request**:
```json
POST /api/analyze-compatibility
Content-Type: application/json

{
  "inputSchema": {
    "format": "xsd",
    "content": "<xs:schema>...</xs:schema>"
  },
  "outputSchema": {
    "format": "xsd",
    "content": "<xs:schema>...</xs:schema>"
  },
  "options": {
    "strictMode": true,
    "suggestDefaults": true
  }
}
```

**Response**:
```json
{
  "compatible": false,
  "coverage": {
    "fullCoverage": [
      {
        "outputPath": "Invoice.InvoiceNumber",
        "inputPath": "Order.OrderID",
        "mappingType": "direct",
        "confidence": 1.0
      }
    ],
    "missingRequired": [
      {
        "outputPath": "Invoice.TaxID",
        "type": "string",
        "required": true,
        "suggestions": ["constant:UNKNOWN", "prompt:user"]
      }
    ],
    "partialCoverage": []
  },
  "mappingGraph": {
    "nodes": [...],
    "edges": [...]
  },
  "complexity": "moderate",
  "confidence": 0.85
}
```

### Implementation in Daemon

**Location**: `modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/SchemaCompatibilityAnalyzer.kt`

```kotlin
object SchemaCompatibilityAnalyzer {

    /**
     * Analyze compatibility between input and output schemas
     */
    fun analyze(
        inputSchema: SchemaDefinition,
        outputSchema: SchemaDefinition,
        options: AnalysisOptions = AnalysisOptions.DEFAULT
    ): CompatibilityAnalysis {
        val coverage = analyzeCoverage(inputSchema, outputSchema)
        val missing = findMissingFields(inputSchema, outputSchema)
        val partial = findPartialCoverage(inputSchema, outputSchema)

        return CompatibilityAnalysis(
            compatible = missing.none { it.required },
            coverage = Coverage(
                fullCoverage = coverage,
                missingRequired = missing.filter { it.required },
                partialCoverage = partial
            ),
            complexity = estimateComplexity(coverage, missing, partial),
            confidence = calculateConfidence(coverage, missing, partial)
        )
    }

    /**
     * Find fields with direct or calculable mappings
     */
    private fun analyzeCoverage(
        inputSchema: SchemaDefinition,
        outputSchema: SchemaDefinition
    ): List<FieldMapping> {
        val mappings = mutableListOf<FieldMapping>()

        outputSchema.fields.forEach { outputField ->
            // Try direct name matching
            val directMatch = inputSchema.findField(outputField.name)
            if (directMatch != null && typesCompatible(directMatch.type, outputField.type)) {
                mappings.add(
                    FieldMapping(
                        outputPath = outputField.path,
                        inputPath = directMatch.path,
                        mappingType = MappingType.DIRECT,
                        confidence = 1.0
                    )
                )
            } else {
                // Try semantic matching (name similarity, type compatibility)
                val semanticMatch = findSemanticMatch(inputSchema, outputField)
                if (semanticMatch != null) {
                    mappings.add(semanticMatch)
                }

                // Try calculated match (e.g., Total = sum of items)
                val calculatedMatch = findCalculatedMatch(inputSchema, outputField)
                if (calculatedMatch != null) {
                    mappings.add(calculatedMatch)
                }
            }
        }

        return mappings
    }

    /**
     * Find semantic matches using name similarity and type compatibility
     */
    private fun findSemanticMatch(
        inputSchema: SchemaDefinition,
        outputField: SchemaField
    ): FieldMapping? {
        // Use fuzzy matching for field names
        // e.g., "InvoiceNumber" matches "OrderID" with lower confidence
        val candidates = inputSchema.fields.filter { inputField ->
            nameSimilarity(inputField.name, outputField.name) > 0.6 &&
            typesCompatible(inputField.type, outputField.type)
        }

        return candidates.maxByOrNull { inputField ->
            nameSimilarity(inputField.name, outputField.name)
        }?.let { match ->
            FieldMapping(
                outputPath = outputField.path,
                inputPath = match.path,
                mappingType = MappingType.SEMANTIC,
                confidence = nameSimilarity(match.name, outputField.name)
            )
        }
    }

    /**
     * Find calculated matches (aggregations, transformations)
     */
    private fun findCalculatedMatch(
        inputSchema: SchemaDefinition,
        outputField: SchemaField
    ): FieldMapping? {
        // Check for aggregation patterns
        if (outputField.name.contains("total", ignoreCase = true) ||
            outputField.name.contains("sum", ignoreCase = true)) {
            // Look for array of numeric values
            val arrayFields = inputSchema.findArraysOfType(NumericType)
            if (arrayFields.isNotEmpty()) {
                return FieldMapping(
                    outputPath = outputField.path,
                    inputPath = arrayFields.first().path,
                    mappingType = MappingType.AGGREGATED,
                    transformation = "sum(${arrayFields.first().path})",
                    confidence = 0.7
                )
            }
        }

        // Check for other calculation patterns
        // ... more heuristics

        return null
    }
}
```

---

## UI Integration

### Theia Extension Updates

**New Panel**: "Schema Analysis" tab in AI Assistant

```typescript
// Extension: utlx-ai-assistant/src/SchemaAnalysisPanel.tsx

export class SchemaAnalysisPanel extends React.Component {

  async analyzeSchemas() {
    const inputSchema = this.props.inputSchemaPath;
    const outputSchema = this.props.outputSchemaPath;

    // Call MCP tool
    const analysis = await this.mcpClient.call('analyze_schema_compatibility', {
      inputSchema,
      inputFormat: this.detectFormat(inputSchema),
      outputSchema,
      outputFormat: this.detectFormat(outputSchema)
    });

    this.setState({ analysis });
  }

  render() {
    const { analysis } = this.state;

    return (
      <div className="schema-analysis">
        <h2>Schema Compatibility Analysis</h2>

        {/* Coverage Summary */}
        <div className="coverage-summary">
          <ProgressBar
            value={this.calculateCoveragePercent(analysis)}
            label={`${this.calculateCoveragePercent(analysis)}% Compatible`}
          />
        </div>

        {/* Full Coverage */}
        <Section title="✅ Full Coverage" count={analysis.coverage.fullCoverage.length}>
          {analysis.coverage.fullCoverage.map(mapping => (
            <MappingRow key={mapping.outputField}>
              <span className="output-field">{mapping.outputField}</span>
              <span className="arrow">←</span>
              <span className="input-field">{mapping.inputField}</span>
              <Badge type={mapping.mappingType}>{mapping.mappingType}</Badge>
            </MappingRow>
          ))}
        </Section>

        {/* Missing Fields */}
        {analysis.coverage.missingRequired.length > 0 && (
          <Section title="❌ Missing Required Fields" count={analysis.coverage.missingRequired.length}>
            {analysis.coverage.missingRequired.map(missing => (
              <MissingFieldRow key={missing.outputField}>
                <span className="field">{missing.outputField}</span>
                <div className="suggestions">
                  {missing.suggestions.map(suggestion => (
                    <Button onClick={() => this.applySuggestion(missing.outputField, suggestion)}>
                      {suggestion}
                    </Button>
                  ))}
                </div>
              </MissingFieldRow>
            ))}
          </Section>
        )}

        {/* Actions */}
        <div className="actions">
          <Button primary onClick={() => this.generateUTLX()}>
            Generate UTLX
          </Button>
          <Button onClick={() => this.generateVariants()}>
            Show Optimization Variants
          </Button>
        </div>
      </div>
    );
  }
}
```

---

## Implementation Details

### Phase 1: Schema Analysis (New - 2 weeks)

- [ ] **Daemon Extension**:
  - [ ] Implement `SchemaCompatibilityAnalyzer.kt`
  - [ ] Add `/api/analyze-compatibility` endpoint
  - [ ] Implement field matching algorithms:
    - [ ] Direct name matching
    - [ ] Semantic matching (fuzzy name similarity)
    - [ ] Type compatibility checking
    - [ ] Calculated field detection (aggregations)
  - [ ] Add unit tests for analyzer

- [ ] **MCP Tool Implementation**:
  - [ ] Create `analyze_schema_compatibility` tool
  - [ ] Integrate with daemon endpoint
  - [ ] Format analysis results for LLM consumption

### Phase 2: Transformation Variants (New - 2 weeks)

- [ ] **Daemon Extension**:
  - [ ] Implement transformation optimization strategies
  - [ ] Add performance estimation logic
  - [ ] Create variant generator

- [ ] **MCP Tool Implementation**:
  - [ ] Create `generate_transformation_variants` tool
  - [ ] Implement optimization heuristics:
    - [ ] Speed: Minimize iterations
    - [ ] Memory: Avoid intermediate variables
    - [ ] Readability: Use let bindings, clear names
  - [ ] Generate multiple UTLX variants

### Phase 3: UI Integration (New - 1 week)

- [ ] **Theia Extension**:
  - [ ] Create Schema Analysis panel
  - [ ] Add coverage visualization
  - [ ] Implement gap resolution UI
  - [ ] Add variant selection UI

---

## Success Metrics

### Coverage Analysis Accuracy
- **Target**: >90% accuracy in identifying direct mappings
- **Measurement**: Compare AI-suggested mappings with human expert mappings

### Gap Detection Precision
- **Target**: 100% precision (no false positives for missing fields)
- **Measurement**: Validate that all flagged gaps are actually missing

### Variant Quality
- **Target**: >80% of users select AI-recommended "balanced" variant
- **Measurement**: Track variant selection in telemetry

### User Satisfaction
- **Target**: >4.5/5.0 rating for schema-to-schema generation
- **Measurement**: In-app feedback after UTLX generation

---

## Example: Complete Workflow

```
1. User Action: Loads order.xsd → Input Panel, invoice.xsd → Output Panel

2. AI: Automatically analyzes compatibility

   [Coverage Analysis shown in UI]

   ✅ 85% Compatible
   • 6 direct mappings
   • 3 calculated fields
   • 1 missing required field (TaxID)

3. User: Clicks "View Details"

   [Detailed mapping shown]

   ✅ InvoiceNumber ← Order.OrderID
   ✅ InvoiceDate ← Order.OrderDate
   ✅ CustomerName ← Order.Customer.Name
   ✅ CustomerEmail ← Order.Customer.Email (optional)
   ✅ Subtotal ← sum(Order.Items.Item amounts) [calculated]
   ❌ TaxID - NOT AVAILABLE
   ⚠️ Tax - Can calculate with rate assumption

4. User: Clicks "Resolve Missing Fields"

   [Modal dialog]

   Missing Field: Invoice.TaxID (required)

   Suggestions:
   • Use constant: [UNKNOWN___] [Apply]
   • Use expression: $[___________] [Apply]
   • Skip (will cause validation error)

   User enters: "NOT_PROVIDED" → Clicks Apply

5. User: Clicks "Generate UTLX"

   [AI generates 3 variants, shows in side panel]

   Variant 1: BALANCED ⭐ (Recommended)
   • Speed: Medium | Memory: Medium | Readability: 8/10
   [Preview] [Select]

   User: Clicks "Select" on Variant 1

6. UTLX inserted into editor:

   %utlx 1.0
   input xml
   output xml
   ---
   let items = $input.Order.Items.Item
   let lineItems = items |> map(item => {
     description: item.ProductID,
     amount: item.Quantity * item.UnitPrice
   })
   let subtotal = sum(lineItems |> map(li => li.amount))

   {
     Invoice: {
       InvoiceNumber: $input.Order.OrderID,
       InvoiceDate: $input.Order.OrderDate,
       CustomerName: $input.Order.Customer.Name,
       CustomerEmail: $input.Order.Customer.Email ?? "no-email@example.com",
       TaxID: "NOT_PROVIDED",
       LineItems: { LineItem: lineItems },
       Subtotal: subtotal,
       Tax: subtotal * 0.10,  /* Assumes 10% tax rate */
       Total: subtotal * 1.10
     }
   }

7. User: Reviews, tests, optionally refines

   [Output panel shows validation: ✅ Valid UTLX]
   [User can now test with sample data]
```

---

## Summary

This schema-to-schema analysis capability adds three critical features:

1. **Coverage Analysis**: Understand what can and cannot be mapped
2. **Gap Identification**: Clearly identify missing required fields
3. **Optimization Variants**: Choose performance/readability trade-offs

**Integration with Existing MCP Architecture**:
- Adds 2 new MCP tools (Tool 7 & 8)
- Extends daemon with compatibility analyzer
- Enhances Theia UI with analysis panel
- Fits within existing 4-phase roadmap

**Timeline Impact**: +3 weeks total (can be done in Phase 4 as "Advanced Features")

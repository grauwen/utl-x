# UTL-X Schema Analysis & Generation Module

## Overview

The Analysis module provides **design-time schema analysis and generation** for UTL-X transformations.

## Architecture

```
Design Time Flow:
┌─────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Input Schema   │────→│  UTL-X Transform │────→│  Output Schema   │
│  (XSD/JSON)     │     │  (.utlx file)    │     │  (XSD/JSON/CSV)  │
└─────────────────┘     └──────────────────┘     └──────────────────┘
         │                       │                         │
         └───────────────────────┴─────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │  Schema Inference       │
                    │  & Type Analysis        │
                    └─────────────────────────┘
```

## Components

### 1. Input Schema Parser

Parse various schema formats into UTL-X type system:

```kotlin
interface InputSchemaParser {
    fun parse(schema: String, format: SchemaFormat): TypeDefinition
}

// Implementations:
class XSDSchemaParser : InputSchemaParser
class JSONSchemaParser : InputSchemaParser
class CSVSchemaParser : InputSchemaParser
class YAMLSchemaParser : InputSchemaParser
```

### 2. Type Inference Engine

Analyze .utlx transformations to infer output types:

```kotlin
interface TypeInference {
    fun inferOutputType(
        transformation: Program,
        inputType: TypeDefinition
    ): TypeDefinition
}

class StaticTypeInference : TypeInference {
    // Analyzes transformation AST
    // Tracks data flow through operations
    // Infers resulting structure
}
```

### 3. Schema Generator

Generate schemas in target formats:

```kotlin
interface SchemaGenerator {
    fun generate(
        type: TypeDefinition,
        format: SchemaFormat,
        options: GeneratorOptions
    ): String
}

// Implementations:
class XSDGenerator : SchemaGenerator
class JSONSchemaGenerator : SchemaGenerator
class CSVSchemaGenerator : SchemaGenerator
```

### 4. Validation Engine

Validate transformations against schemas:

```kotlin
interface TransformValidator {
    fun validate(
        transformation: Program,
        inputSchema: TypeDefinition,
        expectedOutputSchema: TypeDefinition?
    ): ValidationResult
}
```

## Usage Examples

### CLI Usage

```bash
# Generate output schema from input schema + transformation
utlx schema generate \
  --input-schema order.xsd \
  --transform order-to-invoice.utlx \
  --output-format json-schema \
  --output invoice-schema.json

# Validate transformation against schemas
utlx schema validate \
  --input-schema order.xsd \
  --transform order-to-invoice.utlx \
  --expected-output invoice-schema.json

# Infer output schema without input schema (best effort)
utlx schema infer \
  --transform user-processing.utlx \
  --output-format xsd
```

### Programmatic API

```kotlin
// Parse input schema
val xsdParser = XSDSchemaParser()
val inputType = xsdParser.parse(File("order.xsd").readText())

// Parse transformation
val parser = Parser()
val transform = parser.parse(File("order-to-invoice.utlx").readText())

// Infer output type
val typeInference = StaticTypeInference()
val outputType = typeInference.inferOutputType(transform, inputType)

// Generate output schema
val jsonSchemaGen = JSONSchemaGenerator()
val outputSchema = jsonSchemaGen.generate(
    outputType, 
    SchemaFormat.JSON_SCHEMA,
    GeneratorOptions(pretty = true)
)

println(outputSchema)
```

### Integration with Build Tools

#### Gradle Plugin

```kotlin
// build.gradle.kts
plugins {
    id("org.apache.utlx.schema") version "0.9.0"
}

utlxSchema {
    transformations {
        register("orderToInvoice") {
            inputSchema.set(file("schemas/order.xsd"))
            transform.set(file("transforms/order-to-invoice.utlx"))
            outputFormat.set("json-schema")
            outputFile.set(file("build/generated-schemas/invoice-schema.json"))
        }
    }
}
```

#### Maven Plugin

```xml
<plugin>
    <groupId>org.apache.utlx</groupId>
    <artifactId>utlx-maven-plugin</artifactId>
    <version>0.9.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate-schema</goal>
            </goals>
            <configuration>
                <inputSchema>schemas/order.xsd</inputSchema>
                <transform>transforms/order-to-invoice.utlx</transform>
                <outputFormat>json-schema</outputFormat>
                <outputFile>target/generated-schemas/invoice-schema.json</outputFile>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Type System

### Internal Type Representation

```kotlin
sealed class TypeDefinition {
    data class Scalar(
        val kind: ScalarKind,
        val constraints: List<Constraint> = emptyList()
    ) : TypeDefinition()
    
    data class Array(
        val elementType: TypeDefinition,
        val minItems: Int? = null,
        val maxItems: Int? = null
    ) : TypeDefinition()
    
    data class Object(
        val properties: Map<String, Property>,
        val required: Set<String> = emptySet(),
        val additionalProperties: Boolean = false
    ) : TypeDefinition()
    
    data class Union(
        val types: List<TypeDefinition>
    ) : TypeDefinition()
    
    object Any : TypeDefinition()
}

enum class ScalarKind {
    STRING, INTEGER, NUMBER, BOOLEAN, NULL, DATE, DATETIME
}

data class Property(
    val type: TypeDefinition,
    val nullable: Boolean = false,
    val description: String? = null
)

data class Constraint(
    val kind: ConstraintKind,
    val value: Any
)

enum class ConstraintKind {
    MIN_LENGTH, MAX_LENGTH, PATTERN,
    MINIMUM, MAXIMUM, ENUM
}
```

## Type Inference Algorithm

### 1. Parse Input Schema → TypeDefinition

```kotlin
// XSD Example
<xs:element name="Order">
    <xs:complexType>
        <xs:sequence>
            <xs:element name="id" type="xs:string"/>
            <xs:element name="total" type="xs:decimal"/>
        </xs:sequence>
    </xs:complexType>
</xs:element>

// Becomes:
TypeDefinition.Object(
    properties = mapOf(
        "Order" to Property(
            type = TypeDefinition.Object(
                properties = mapOf(
                    "id" to Property(TypeDefinition.Scalar(ScalarKind.STRING)),
                    "total" to Property(TypeDefinition.Scalar(ScalarKind.NUMBER))
                )
            )
        )
    )
)
```

### 2. Analyze Transformation AST

```kotlin
// UTL-X Transformation
%utlx 1.0
input xml
output json
---
{
  invoice: {
    invoiceId: input.Order.@id,
    amount: input.Order.Total,
    items: input.Order.Items.Item |> map(item => {
      sku: item.@sku,
      quantity: item.@quantity,
      price: item.@price
    })
  }
}

// Type Inference tracks:
// 1. input.Order.@id → String
// 2. input.Order.Total → Number
// 3. input.Order.Items.Item → Array<Object>
// 4. map operation preserves array, transforms element type
```

### 3. Generate Output Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "invoice": {
      "type": "object",
      "properties": {
        "invoiceId": { "type": "string" },
        "amount": { "type": "number" },
        "items": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "sku": { "type": "string" },
              "quantity": { "type": "string" },
              "price": { "type": "string" }
            },
            "required": ["sku", "quantity", "price"]
          }
        }
      },
      "required": ["invoiceId", "amount", "items"]
    }
  },
  "required": ["invoice"]
}
```

## Advanced Features

### 1. Schema Evolution Tracking

```bash
# Detect breaking changes between schema versions
utlx schema diff \
  --old-schema invoice-v1-schema.json \
  --new-schema invoice-v2-schema.json \
  --output diff-report.html
```

### 2. Multiple Output Schemas

```utlx
%utlx 1.0
input xml
output {
  summary: json,
  details: xml
}
---
output.summary = { ... }
output.details = { ... }
```

```bash
# Generate schemas for all outputs
utlx schema generate \
  --input-schema order.xsd \
  --transform multi-output.utlx \
  --output-dir build/schemas/
```

### 3. Schema Documentation

```bash
# Generate HTML documentation from schemas
utlx schema document \
  --input-schema order.xsd \
  --transform order-to-invoice.utlx \
  --output docs/transformation-spec.html
```

### 4. Contract Testing

```kotlin
@Test
fun `transformation produces valid output schema`() {
    val validator = SchemaValidator()
    val result = validator.validate(
        inputData = orderXML,
        transformation = orderToInvoiceTransform,
        expectedSchema = invoiceSchema
    )
    assertTrue(result.isValid)
}
```

## Integration Points

### 1. IDE Support

```kotlin
// IntelliJ/VS Code plugin uses this for:
// - Schema-aware autocomplete
// - Real-time validation
// - Schema preview in editor

class SchemaPreviewProvider {
    fun generateLivePreview(
        transformation: String,
        inputSchema: String?
    ): String {
        // Generate and display output schema in sidebar
    }
}
```

### 2. CI/CD Pipeline

```yaml
# .github/workflows/validate-schemas.yml
- name: Generate and validate schemas
  run: |
    utlx schema generate-all --transforms transforms/ --schemas schemas/
    utlx schema validate-all --transforms transforms/ --test-data test-data/
```

### 3. API Gateway Integration

```kotlin
// Generate OpenAPI spec from transformations
val openApiGen = OpenAPIGenerator()
val spec = openApiGen.generateFromTransform(
    transform = orderToInvoice,
    inputSchema = orderSchema,
    endpoint = "/api/orders/transform"
)
```

## Implementation Roadmap

### Phase 1: Core Infrastructure (Months 1-2)
- Type system implementation
- Basic XSD parser
- Basic JSON Schema parser
- Simple type inference for common operations

### Phase 2: Schema Generation (Months 3-4)
- JSON Schema generator
- XSD generator
- CSV schema generator
- CLI commands

### Phase 3: Advanced Analysis (Months 5-6)
- Complex type inference (conditionals, functions)
- Schema validation
- Error reporting
- Build tool plugins

### Phase 4: Tooling (Months 7-8)
- IDE integration
- Schema diff/evolution
- Documentation generation
- Contract testing support

## Benefits

### For Developers
✅ Catch schema mismatches at design-time  
✅ Auto-generate API documentation  
✅ Better IDE support (autocomplete, validation)  
✅ Faster development with immediate feedback  

### For Integration Teams
✅ Document data contracts clearly  
✅ Validate transformations before deployment  
✅ Track schema evolution over time  
✅ Generate test data from schemas  

### For UTL-X Project
✅ Differentiation from DataWeave/XSLT  
✅ Enterprise-friendly feature  
✅ Enables design-first workflows  
✅ Foundation for advanced tooling  

## Conclusion

Schema analysis and generation is a **perfect fit** for UTL-X and addresses a critical gap in the data transformation space. It should be implemented as a separate `modules/analysis` module that integrates with the core type system and CLI.

This feature would be a **major selling point** for enterprise adoption and positions UTL-X as a modern, design-time-aware transformation language.

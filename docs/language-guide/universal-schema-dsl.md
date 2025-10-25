# Universal Schema DSL

**Version:** 1.0
**Status:** Draft
**Last Updated:** 2025-10-26

## Overview

The **Universal Schema DSL** is a format-agnostic schema definition language that enables schema transformations across multiple schema formats (XSD, JSON Schema, Protobuf, Avro, etc.) using a single transformation definition.

### Philosophy

UTL-X's core philosophy is **format abstraction**. Just as the Universal Data Model (UDM) abstracts XML, JSON, CSV, and other data formats, the Universal Schema DSL abstracts **schema formats**.

### Key Benefits

✅ **Write once, output anywhere**: Same transformation works for `output xsd` or `output jsch`
✅ **Schema-to-schema transformations**: Natural JSCH ↔ XSD, XSD ↔ Protobuf, etc.
✅ **Format-agnostic**: No need to learn XSD XML structure or JSON Schema syntax
✅ **Enterprise-friendly**: Model schemas in CSV, output to production formats
✅ **Future-proof**: Easily add new schema formats (GraphQL, Avro, Thrift)

---

## Core Structure

```kotlin
{
  // Schema metadata
  namespace: String?,           // XSD targetNamespace / base URI
  id: String?,                  // JSON Schema $id / unique identifier
  version: String?,             // Schema version
  elementFormDefault: String?,  // XSD-specific: "qualified" | "unqualified"

  // Type definitions (format-agnostic)
  types: {
    TypeName: TypeDefinition
  }
}
```

---

## Type Definitions

### Structure Type (Object/ComplexType)

Represents an object (JSON Schema) or complex type (XSD).

```kotlin
{
  kind: "structure",
  documentation: String?,       // Human-readable description

  fields: [
    {
      name: String,             // Field/element name
      type: String,             // Primitive or type reference
      required: Boolean?,       // Is this field required? (default: false)
      description: String?,     // Field-level documentation
      array: Boolean?,          // Is this field an array? (default: false)
      default: Any?,            // Default value

      // Universal constraints
      constraints: {
        // String constraints
        minLength: Integer?,
        maxLength: Integer?,
        pattern: String?,       // Regex pattern

        // Numeric constraints
        minimum: Number?,
        maximum: Number?,
        exclusiveMinimum: Number?,
        exclusiveMaximum: Number?,

        // General constraints
        enum: [Any]?,           // Allowed values
        format: String?         // email, uri, date, date-time, etc.
      }?
    }
  ]
}
```

**Example:**
```kotlin
{
  types: {
    Customer: {
      kind: "structure",
      documentation: "Customer information",
      fields: [
        {
          name: "customerId",
          type: "string",
          required: true,
          description: "Unique customer identifier",
          constraints: {
            pattern: "[A-Z]{3}-[0-9]{6}"
          }
        },
        {
          name: "email",
          type: "string",
          required: true,
          description: "Contact email address",
          constraints: {
            format: "email"
          }
        },
        {
          name: "age",
          type: "integer",
          required: false,
          constraints: {
            minimum: 0,
            maximum: 150
          }
        }
      ]
    }
  }
}
```

---

### Enumeration Type

Represents a set of allowed values.

```kotlin
{
  kind: "enumeration",
  documentation: String?,
  baseType: String?,            // Base type (default: "string")

  values: [
    String |                    // Simple value
    {
      value: String,            // Enumeration value
      description: String?      // Value documentation
    }
  ]
}
```

**Example:**
```kotlin
{
  types: {
    OrderStatus: {
      kind: "enumeration",
      documentation: "Possible order statuses",
      values: [
        {value: "pending", description: "Order received, not yet processed"},
        {value: "processing", description: "Order is being prepared"},
        {value: "shipped", description: "Order has been shipped"},
        {value: "delivered", description: "Order delivered to customer"},
        {value: "cancelled", description: "Order was cancelled"}
      ]
    }
  }
}
```

---

### Array Type

Represents an array/sequence of items.

```kotlin
{
  kind: "array",
  documentation: String?,
  itemType: String,             // Type of array elements

  constraints: {
    minItems: Integer?,
    maxItems: Integer?,
    uniqueItems: Boolean?
  }?
}
```

**Example:**
```kotlin
{
  types: {
    EmailList: {
      kind: "array",
      documentation: "List of email addresses",
      itemType: "string",
      constraints: {
        minItems: 1,
        uniqueItems: true
      }
    }
  }
}
```

---

### Union Type (OneOf/Choice)

Represents a choice between multiple types.

```kotlin
{
  kind: "union",
  documentation: String?,
  options: [String]             // List of type names
}
```

**Example:**
```kotlin
{
  types: {
    PaymentMethod: {
      kind: "union",
      documentation: "Payment can be credit card or bank transfer",
      options: ["CreditCardPayment", "BankTransferPayment"]
    }
  }
}
```

---

### Primitive Type

For creating constrained primitives (simpleType in XSD).

```kotlin
{
  kind: "primitive",
  baseType: String,             // string, integer, number, boolean, date, etc.
  documentation: String?,

  constraints: {
    // (same as field constraints)
  }?
}
```

**Example:**
```kotlin
{
  types: {
    EmailAddress: {
      kind: "primitive",
      baseType: "string",
      documentation: "Valid email address",
      constraints: {
        format: "email",
        pattern: "[^@]+@[^@]+"
      }
    }
  }
}
```

---

## Primitive Types

The following primitive types are supported universally:

| Universal Type | XSD Type | JSON Schema Type | Notes |
|----------------|----------|------------------|-------|
| `string` | `xs:string` | `"type": "string"` | Text |
| `integer` | `xs:integer` | `"type": "integer"` | Whole numbers |
| `number` | `xs:decimal` | `"type": "number"` | Decimal numbers |
| `boolean` | `xs:boolean` | `"type": "boolean"` | true/false |
| `date` | `xs:date` | `"type": "string", "format": "date"` | Date only |
| `datetime` | `xs:dateTime` | `"type": "string", "format": "date-time"` | Date and time |
| `time` | `xs:time` | `"type": "string", "format": "time"` | Time only |
| `binary` | `xs:base64Binary` | `"type": "string", "contentEncoding": "base64"` | Binary data |
| `uri` | `xs:anyURI` | `"type": "string", "format": "uri"` | URI/URL |

---

## Mapping to XSD

### Structure → ComplexType

**Universal DSL:**
```kotlin
{
  types: {
    Customer: {
      kind: "structure",
      documentation: "Customer data",
      fields: [
        {name: "id", type: "string", required: true},
        {name: "email", type: "string", required: false}
      ]
    }
  }
}
```

**Generated XSD (Venetian Blind pattern):**
```xml
<xs:complexType name="Customer">
  <xs:annotation>
    <xs:documentation>Customer data</xs:documentation>
  </xs:annotation>
  <xs:sequence>
    <xs:element name="id" type="xs:string"/>
    <xs:element name="email" type="xs:string" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>
```

### Enumeration → SimpleType with Restriction

**Universal DSL:**
```kotlin
{
  types: {
    Status: {
      kind: "enumeration",
      values: ["active", "inactive", "suspended"]
    }
  }
}
```

**Generated XSD:**
```xml
<xs:simpleType name="Status">
  <xs:restriction base="xs:string">
    <xs:enumeration value="active"/>
    <xs:enumeration value="inactive"/>
    <xs:enumeration value="suspended"/>
  </xs:restriction>
</xs:simpleType>
```

### Constraints → XSD Facets

| Universal Constraint | XSD Facet |
|----------------------|-----------|
| `minLength` | `<xs:minLength>` |
| `maxLength` | `<xs:maxLength>` |
| `pattern` | `<xs:pattern>` |
| `minimum` | `<xs:minInclusive>` |
| `maximum` | `<xs:maxInclusive>` |
| `enum` | `<xs:enumeration>` (multiple) |

---

## Mapping to JSON Schema

### Structure → Object

**Universal DSL:**
```kotlin
{
  types: {
    Customer: {
      kind: "structure",
      documentation: "Customer data",
      fields: [
        {name: "id", type: "string", required: true},
        {name: "email", type: "string", required: false}
      ]
    }
  }
}
```

**Generated JSON Schema (2020-12):**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$defs": {
    "Customer": {
      "type": "object",
      "description": "Customer data",
      "properties": {
        "id": {"type": "string"},
        "email": {"type": "string"}
      },
      "required": ["id"]
    }
  }
}
```

### Enumeration → Enum

**Universal DSL:**
```kotlin
{
  types: {
    Status: {
      kind: "enumeration",
      values: ["active", "inactive"]
    }
  }
}
```

**Generated JSON Schema:**
```json
{
  "$defs": {
    "Status": {
      "enum": ["active", "inactive"]
    }
  }
}
```

### Constraints → JSON Schema Keywords

| Universal Constraint | JSON Schema Keyword |
|----------------------|---------------------|
| `minLength` | `"minLength"` |
| `maxLength` | `"maxLength"` |
| `pattern` | `"pattern"` |
| `minimum` | `"minimum"` |
| `maximum` | `"maximum"` |
| `enum` | `"enum"` |
| `format` | `"format"` |

---

## Complete Example: CSV to Multiple Schemas

### Input CSV (Schema Metadata):
```csv
fieldName,type,required,minOccurs,maxOccurs,documentation
customerId,string,true,1,1,Unique customer identifier
email,string,true,1,1,Contact email address
age,integer,false,0,1,Customer age (optional)
status,string,true,1,1,Account status (active/inactive/suspended)
```

### Transformation (Universal DSL):
```utlx
%utlx 1.0
input csv
output xsd  // ← Change to 'jsch' and it works!
---
{
  namespace: "http://example.com/customer",
  version: "1.0",

  types: {
    Customer: {
      kind: "structure",
      documentation: "Customer information from CRM system",

      fields: map($input, field => {
        name: field.fieldName,
        type: field.type,
        required: field.required == true,
        description: field.documentation,

        constraints: if (field.fieldName == "status") {
          enum: ["active", "inactive", "suspended"]
        } else null
      })
    }
  }
}
```

### Output as XSD:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://example.com/customer"
           version="1.0"
           elementFormDefault="qualified">
  <xs:complexType name="Customer">
    <xs:annotation>
      <xs:documentation>Customer information from CRM system</xs:documentation>
    </xs:annotation>
    <xs:sequence>
      <xs:element name="customerId" type="xs:string">
        <xs:annotation><xs:documentation>Unique customer identifier</xs:documentation></xs:annotation>
      </xs:element>
      <xs:element name="email" type="xs:string">
        <xs:annotation><xs:documentation>Contact email address</xs:documentation></xs:annotation>
      </xs:element>
      <xs:element name="age" type="xs:integer" minOccurs="0">
        <xs:annotation><xs:documentation>Customer age (optional)</xs:documentation></xs:annotation>
      </xs:element>
      <xs:element name="status" minOccurs="1">
        <xs:simpleType>
          <xs:restriction base="xs:string">
            <xs:enumeration value="active"/>
            <xs:enumeration value="inactive"/>
            <xs:enumeration value="suspended"/>
          </xs:restriction>
        </xs:simpleType>
        <xs:annotation><xs:documentation>Account status (active/inactive/suspended)</xs:documentation></xs:annotation>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

### Output as JSON Schema (change line 3 to `output jsch`):
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "http://example.com/customer",
  "version": "1.0",
  "$defs": {
    "Customer": {
      "type": "object",
      "description": "Customer information from CRM system",
      "properties": {
        "customerId": {
          "type": "string",
          "description": "Unique customer identifier"
        },
        "email": {
          "type": "string",
          "description": "Contact email address"
        },
        "age": {
          "type": "integer",
          "description": "Customer age (optional)"
        },
        "status": {
          "type": "string",
          "enum": ["active", "inactive", "suspended"],
          "description": "Account status (active/inactive/suspended)"
        }
      },
      "required": ["customerId", "email", "status"]
    }
  }
}
```

**Same transformation, different output format!**

---

## Schema-to-Schema Transformations

### JSON Schema → XSD

**Input** (JSON Schema):
```json
{
  "type": "object",
  "title": "Customer",
  "properties": {
    "customerId": {"type": "string"},
    "email": {"type": "string", "format": "email"}
  },
  "required": ["customerId"]
}
```

**Transformation:**
```utlx
%utlx 1.0
input jsch
output xsd {pattern: "venetian-blind"}
---
{
  namespace: "http://soap.example.com/customer",

  types: {
    [$input.title ?? "Root"]: {
      kind: "structure",
      documentation: $input.description,

      fields: map(entries($input.properties), ([name, prop]) => {
        name: name,
        type: prop.type,
        required: contains($input.required ?? [], name),
        description: prop.description,
        constraints: {
          ...if (prop.format != null) {format: prop.format},
          ...if (prop.pattern != null) {pattern: prop.pattern},
          ...if (prop.minimum != null) {minimum: prop.minimum},
          ...if (prop.maximum != null) {maximum: prop.maximum}
        }
      })
    }
  }
}
```

**Output** (XSD):
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://soap.example.com/customer">
  <xs:complexType name="Customer">
    <xs:sequence>
      <xs:element name="customerId" type="xs:string"/>
      <xs:element name="email" minOccurs="0">
        <xs:simpleType>
          <xs:restriction base="xs:string">
            <xs:pattern value="[^@]+@[^@]+"/>
          </xs:restriction>
        </xs:simpleType>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

---

## XSD Pattern Enforcement

When `output xsd {pattern: "..."}` is specified, the serializer enforces structural rules:

### Venetian Blind (Recommended)
- Global types (`xs:complexType`, `xs:simpleType`)
- Local elements (within types)
- Best for: Reusability, large schemas

### Russian Doll
- Single global element
- All types inline (nested)
- Best for: Small, encapsulated schemas

### Salami Slice
- All elements global
- Minimal types
- Best for: Flexible composition

### Garden of Eden
- All elements AND types global
- Best for: Maximum reusability

The Universal DSL is **pattern-agnostic**. The serializer structures the output based on the `pattern` option.

---

## Helper Functions (Stdlib)

### `SimpleSchema(config)`

For simple CSV → Schema cases:

```utlx
%utlx 1.0
input csv
output xsd
---
SimpleSchema({
  namespace: "http://example.com",
  root: "customer",
  fields: $input
})
```

Expands to full Universal DSL with single structure type.

### `MapFromJsonSchema(config)`

Convert JSON Schema to Universal DSL:

```utlx
%utlx 1.0
input jsch
output xsd
---
MapFromJsonSchema({
  namespace: "http://example.com",
  schema: $input
})
```

### `MapFromXSD(config)`

Convert XSD (via metadata) to Universal DSL:

```utlx
%utlx 1.0
input xsd
output jsch
---
MapFromXSD({
  id: "https://example.com/schema",
  schema: $input
})
```

---

## Hybrid Mode

Serializers support two modes:

### Low-Level Mode (Pass-Through)

User manually constructs format-specific structure:

```utlx
output xsd
---
{
  "xs:schema": {
    "xs:element": { ... }  // Manual XSD XML construction
  }
}
```

**Use when**: Need full control, edge cases, XSD features not in Universal DSL

### High-Level Mode (Universal DSL)

User provides format-agnostic schema definition:

```utlx
output xsd
---
{
  types: {
    Customer: {
      kind: "structure",
      fields: [ ... ]
    }
  }
}
```

**Use when**: Standard schemas, format abstraction, schema-to-schema

**Auto-detection**: Serializer detects presence of `types` (high-level) vs `xs:schema` (low-level)

---

## Future Extensions

### Additional Schema Formats

- **Protobuf**: `output proto`
- **Avro**: `output avro`
- **GraphQL Schema**: `output graphql`
- **Thrift**: `output thrift`

All would consume the same Universal DSL!

### Additional Type Kinds

- `kind: "map"` - Key-value pairs
- `kind: "tuple"` - Fixed-length heterogeneous arrays
- `kind: "reference"` - External schema references

### Additional Constraints

- `multipleOf` - Numeric multiples
- `dependencies` - Conditional requirements
- `allOf`, `anyOf` - Schema composition

---

## Best Practices

1. **Use Universal DSL for standard schemas**
   - Enables format flexibility
   - Easier to understand than format-specific syntax

2. **Use low-level mode for edge cases**
   - Full control when needed
   - Access to format-specific features

3. **Model in CSV for enterprise**
   - Non-technical stakeholders can maintain
   - Version control friendly
   - Transform to production schemas (XSD/JSCH)

4. **Leverage helper functions**
   - `SimpleSchema()` for basic cases
   - `MapFromJsonSchema()` / `MapFromXSD()` for conversions

5. **Document with `documentation` and `description`**
   - Becomes `xs:annotation` in XSD
   - Becomes `description` in JSON Schema
   - Living documentation

---

## References

- **UTL-X Language Guide**: Core language features
- **UDM Specification**: Universal Data Model
- **XSD 1.1 Specification**: W3C XML Schema
- **JSON Schema 2020-12**: JSON Schema specification
- **XSD Design Patterns**: Venetian Blind, Russian Doll, Salami Slice, Garden of Eden

---

**Version History:**
- **1.0** (2025-10-26): Initial specification

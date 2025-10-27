# Universal Schema DSL (USDL)

**Version:** 1.0
**Status:** Draft
**Last Updated:** 2025-10-27

---

## Table of Contents

1. [Overview](#overview)
2. [USDL Directive Syntax](#usdl-directive-syntax)
3. [Complete Directive Catalog](#complete-directive-catalog)
4. [Format Coverage Matrix](#format-coverage-matrix)
5. [Core Type Definitions](#core-type-definitions)
6. [Complete Examples](#complete-examples)
7. [Validation Rules](#validation-rules)
8. [Supported Schema Languages](#supported-schema-languages)

---

## Overview

### What is USDL?

**USDL (Universal Schema Definition Language)** is a format-agnostic schema definition language that enables schema transformations across multiple schema formats using a single transformation definition.

### Philosophy

UTL-X's core philosophy is **format abstraction**. Just as the Universal Data Model (UDM) abstracts XML, JSON, CSV, and other data formats, USDL abstracts **schema formats**.

### Key Benefits

✅ **Write once, output anywhere**: Same transformation works for `output xsd %usdl 1.0` or `output jsch %usdl 1.0`
✅ **Schema-to-schema transformations**: Natural JSCH ↔ XSD, XSD ↔ Protobuf, etc.
✅ **Format-agnostic**: No need to learn XSD XML structure or JSON Schema syntax
✅ **Enterprise-friendly**: Model schemas in CSV, output to production formats
✅ **Future-proof**: Comprehensive directive catalog supports 15+ schema languages
✅ **Stable**: USDL 1.0 directive namespace is frozen - no breaking changes

### Supported Schema Languages (Current + Future)

**Tier 1 (Implemented):** XSD, JSON Schema
**Tier 2 (Planned):** Protobuf, SQL DDL, Apache Avro, GraphQL, OData
**Tier 3 (Future):** OpenAPI, AsyncAPI, Apache Thrift, Parquet
**Tier 4 (Specialized):** Cap'n Proto, FlatBuffers, ASN.1

---

## USDL Directive Syntax

### Explicit Directive Markers

All USDL keywords use the `%` prefix to distinguish them from user data:

```utlx
%utlx 1.0
output xsd %usdl 1.0
---
{
  %namespace: "http://example.com",  // ← USDL directive
  %types: {
    Customer: {                       // ← User type name
      %kind: "structure",             // ← USDL directive
      %fields: [...]
    }
  }
}
```

**Why `%` prefix?**
- **Clear semantics**: `%xxx` = USDL keyword, not user data
- **No collisions**: User can have type named "namespace", "types", "kind"
- **Validation**: Parser can detect typos (`%namepsace` → error)
- **Consistency**: Matches `%utlx 1.0` and `%usdl 1.0` convention
- **Tooling**: IDEs can autocomplete `%` directives

### Core Structure

```utlx
{
  // Schema metadata
  %namespace: String?,
  %version: String?,

  // Type definitions (required)
  %types: {
    TypeName: {
      %kind: "structure" | "enumeration" | "primitive" | "array" | "union" | ...,
      %documentation: String?,
      %fields: [...],
      ...
    }
  }
}
```

---

## Complete Directive Catalog

USDL 1.0 defines **80+ directives** organized into **4 tiers**:

### Tier 1: Core (Required)

**All serializers MUST support these directives.**

| Directive | Type | Description |
|-----------|------|-------------|
| `%namespace` | String | Schema namespace/ID (XSD targetNamespace, JSON Schema $id base) |
| `%version` | String | Schema version |
| `%types` | Object | Type definitions (REQUIRED) |
| `%kind` | String | Type kind: "structure", "enumeration", "primitive", "array", "union", "interface" |
| `%name` | String | Field/element name |
| `%type` | String | Data type (primitive or type reference) |
| `%description` | String | Field-level documentation |
| `%documentation` | String | Type-level documentation (alias for %description) |

---

### Tier 2: Common (Recommended)

**Most serializers SHOULD support these directives.**

#### Data Modeling

| Directive | Type | Description |
|-----------|------|-------------|
| `%fields` | Array | Field definitions for structures |
| `%values` | Array | Enumeration values |
| `%itemType` | String | Array element type |
| `%baseType` | String | Base type for primitives |
| `%default` | Any | Default value |
| `%required` | Boolean | Is field required? (default: false) |
| `%array` | Boolean | Is field an array? (default: false) |
| `%nullable` | Boolean | Explicitly nullable |

#### Constraints

| Directive | Type | Applies To | Description |
|-----------|------|------------|-------------|
| `%constraints` | Object | Fields | Constraint object containing below directives |
| `%minLength` | Integer | String | Minimum string length |
| `%maxLength` | Integer | String | Maximum string length |
| `%pattern` | String | String | Regex pattern validation |
| `%minimum` | Number | Numeric | Minimum value (inclusive) |
| `%maximum` | Number | Numeric | Maximum value (inclusive) |
| `%exclusiveMinimum` | Number | Numeric | Minimum value (exclusive) |
| `%exclusiveMaximum` | Number | Numeric | Maximum value (exclusive) |
| `%enum` | Array | Any | Allowed values |
| `%format` | String | String | Format hint: "email", "uri", "date", "date-time", "uuid", etc. |
| `%multipleOf` | Number | Numeric | Value must be multiple of this |

---

### Tier 3: Format-Specific (Optional)

**Serializers MAY support these based on target format capabilities.**

#### Binary Serialization (Protobuf, Thrift, Cap'n Proto)

| Directive | Type | Format | Description |
|-----------|------|--------|-------------|
| `%fieldNumber` | Integer | Protobuf | Field number (required in Protobuf) |
| `%fieldId` | Integer | Thrift | Field identifier |
| `%ordinal` | Integer | Cap'n Proto | Field ordinal |
| `%precision` | String | Protobuf, Thrift | Integer precision: "int32", "int64", "uint32", "uint64" |
| `%packed` | Boolean | Protobuf | Use packed encoding for repeated fields |
| `%reserved` | Array | Protobuf | Reserved field numbers or names |
| `%oneof` | Object | Protobuf | Discriminated union group |
| `%map` | Object | Protobuf | Map type (key and value types) |

**Example:**
```utlx
%fields: [
  {
    %name: "id",
    %type: "integer",
    %precision: "int64",
    %fieldNumber: 1
  },
  {
    %name: "tags",
    %type: "string",
    %array: true,
    %packed: true,
    %fieldNumber: 2
  }
]
```

#### Big Data (Avro, Parquet)

| Directive | Type | Format | Description |
|-----------|------|--------|-------------|
| `%logicalType` | String | Avro | Logical type: "date", "time-millis", "timestamp-millis", "decimal", "uuid" |
| `%aliases` | Array | Avro | Alternative names for schema evolution |
| `%scale` | Integer | Avro (decimal) | Decimal scale |
| `%size` | Integer | Avro (fixed) | Fixed binary size |
| `%repetition` | String | Parquet | Repetition level: "required", "optional", "repeated" |
| `%encoding` | String | Parquet | Column encoding: "PLAIN", "RLE", etc. |
| `%compression` | String | Parquet | Compression codec: "SNAPPY", "GZIP", etc. |

**Example:**
```utlx
%fields: [
  {
    %name: "createdAt",
    %type: "integer",
    %logicalType: "timestamp-millis",
    %default: 0
  },
  {
    %name: "price",
    %type: "number",
    %logicalType: "decimal",
    %precision: 10,
    %scale: 2
  }
]
```

#### Database (SQL DDL)

| Directive | Type | Description |
|-----------|------|-------------|
| `%key` | Boolean | Primary key field |
| `%autoIncrement` | Boolean | Auto-increment field |
| `%unique` | Boolean | Unique constraint |
| `%index` | Boolean/String | Create index on field |
| `%foreignKey` | Object | Foreign key reference |
| `%references` | String | Referenced table.column (format: "table.column") |
| `%onDelete` | String | Foreign key on delete: "CASCADE", "SET NULL", "RESTRICT" |
| `%onUpdate` | String | Foreign key on update: "CASCADE", "SET NULL", "RESTRICT" |
| `%check` | String | Check constraint expression |
| `%table` | String | Database table name |

**Example:**
```utlx
%types: {
  Customer: {
    %kind: "structure",
    %table: "customers",
    %fields: [
      {
        %name: "id",
        %type: "integer",
        %key: true,
        %autoIncrement: true
      },
      {
        %name: "email",
        %type: "string",
        %unique: true,
        %constraints: {%maxLength: 255}
      },
      {
        %name: "countryId",
        %type: "integer",
        %foreignKey: {
          %references: "countries.id",
          %onDelete: "CASCADE"
        }
      }
    ]
  }
}
```

#### REST/OData

| Directive | Type | Description |
|-----------|------|-------------|
| `%entityType` | Boolean | OData EntityType (vs ComplexType) |
| `%navigation` | Object | Navigation property (relationship) |
| `%target` | String | Navigation target entity type |
| `%cardinality` | String | Navigation cardinality: "one", "many" |
| `%referentialConstraint` | Object | Foreign key for navigation |

**Example:**
```utlx
%types: {
  Customer: {
    %kind: "structure",
    %entityType: true,
    %fields: [
      {%name: "customerId", %type: "string", %key: true},
      {
        %name: "orders",
        %navigation: {
          %target: "Order",
          %cardinality: "many"
        }
      }
    ]
  }
}
```

#### GraphQL

| Directive | Type | Description |
|-----------|------|-------------|
| `%implements` | Array | Implemented interfaces (GraphQL) |
| `%resolver` | String | Resolver function name (future) |

**Example:**
```utlx
%types: {
  Node: {
    %kind: "interface",
    %fields: [
      {%name: "id", %type: "string", %required: true}
    ]
  },
  Customer: {
    %kind: "structure",
    %implements: ["Node"],
    %fields: [
      {%name: "id", %type: "string", %required: true},
      {%name: "email", %type: "string"}
    ]
  }
}
```

#### OpenAPI/Swagger

| Directive | Type | Description |
|-----------|------|-------------|
| `%readOnly` | Boolean | Read-only property |
| `%writeOnly` | Boolean | Write-only property |
| `%discriminator` | Object | Polymorphism discriminator |
| `%propertyName` | String | Discriminator property name |
| `%mapping` | Object | Discriminator value → schema mapping |
| `%externalDocs` | Object | External documentation |
| `%url` | String | External documentation URL |
| `%examples` | Array | Example values |
| `%xml` | Object | XML serialization hints |

---

### Tier 4: Reserved (Future)

**These directives are RESERVED for future USDL versions.**

#### Schema Composition (JSON Schema 2019-09+, GraphQL)

| Directive | Description |
|-----------|-------------|
| `%allOf` | All schemas must match (intersection) |
| `%anyOf` | Any schema must match (union) |
| `%oneOf` | Exactly one schema must match (exclusive union) |
| `%not` | Must not match schema (negation) |
| `%if` | Conditional schema (if condition) |
| `%then` | Schema if condition is true |
| `%else` | Schema if condition is false |

#### Metadata & Documentation

| Directive | Description |
|-----------|-------------|
| `%deprecated` | Deprecation information |
| `%reason` | Deprecation reason |
| `%replacedBy` | Replacement field/type |
| `%title` | Human-readable title |
| `%comment` | Internal comment (not exposed in output) |
| `%tags` | Categorization tags |

#### Advanced

| Directive | Description |
|-----------|-------------|
| `%ref` | Schema reference (URI or local) |
| `%extends` | Schema extension/inheritance |
| `%typedef` | Type alias |
| `%choice` | ASN.1 choice type |
| `%alignment` | Memory alignment (FlatBuffers) |
| `%generic` | Generic type parameter |

---

## Format Coverage Matrix

| Directive | XSD | JSON Schema | Protobuf | SQL | Avro | GraphQL | OData |
|-----------|-----|-------------|----------|-----|------|---------|-------|
| **Core** | | | | | | | |
| %namespace | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| %types | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| %kind | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| %name | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| %type | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| %description | ✅ | ✅ | ✅ | ⚠️ | ✅ | ✅ | ✅ |
| **Common** | | | | | | | |
| %fields | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| %required | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| %array | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| %constraints | ✅ | ✅ | ❌ | ✅ | ⚠️ | ❌ | ⚠️ |
| **Format-Specific** | | | | | | | |
| %fieldNumber | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| %key | ⚠️ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ |
| %logicalType | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| %navigation | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| %implements | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |

**Legend:**
- ✅ Fully supported
- ⚠️ Partially supported
- ❌ Not supported (directive ignored with warning)

---

## Core Type Definitions

### Structure Type

Represents an object/record with named fields.

```utlx
{
  %kind: "structure",
  %documentation: String?,
  %fields: [
    {
      %name: String,
      %type: String,
      %required: Boolean?,
      %description: String?,
      %array: Boolean?,
      %default: Any?,
      %constraints: Object?
    }
  ]
}
```

**Maps to:**
- XSD: `xs:complexType`
- JSON Schema: `"type": "object"`
- Protobuf: `message`
- SQL: `CREATE TABLE`
- Avro: `"type": "record"`

---

### Enumeration Type

Represents a set of allowed values.

```utlx
{
  %kind: "enumeration",
  %documentation: String?,
  %baseType: String?,  // Default: "string"
  %values: [
    String |  // Simple value
    {%value: String, %description: String?}  // With documentation
  ]
}
```

**Maps to:**
- XSD: `xs:simpleType` with `xs:restriction` and `xs:enumeration`
- JSON Schema: `"enum": [...]`
- Protobuf: `enum`
- SQL: `ENUM` or `CHECK` constraint
- Avro: `"type": "enum"`

---

### Primitive Type

Represents a constrained primitive type.

```utlx
{
  %kind: "primitive",
  %baseType: String,  // "string", "integer", "number", etc.
  %documentation: String?,
  %constraints: Object?
}
```

**Use for:** Creating reusable constrained types (e.g., EmailAddress, PositiveInteger)

---

### Array Type

Represents an array/list of items.

```utlx
{
  %kind: "array",
  %documentation: String?,
  %itemType: String,
  %constraints: {
    %minItems: Integer?,
    %maxItems: Integer?,
    %uniqueItems: Boolean?
  }?
}
```

**Maps to:**
- XSD: `xs:complexType` with `xs:sequence` and `maxOccurs="unbounded"`
- JSON Schema: `"type": "array"`
- Protobuf: `repeated`
- Avro: `"type": "array"`

---

### Union Type

Represents a choice between multiple types (oneOf).

```utlx
{
  %kind: "union",
  %documentation: String?,
  %options: [String]  // List of type names
}
```

**Maps to:**
- XSD: `xs:choice`
- JSON Schema: `"oneOf": [...]`
- Protobuf: `oneof`
- Avro: `["null", "string"]` union syntax

---

### Interface Type (GraphQL)

```utlx
{
  %kind: "interface",
  %documentation: String?,
  %fields: [...]
}
```

**Maps to:**
- GraphQL: `interface`
- Avro: Protocol interface (future)

---

## Primitive Types

Universal type names that map to format-specific types:

| Universal | XSD | JSON Schema | Protobuf | SQL | Avro |
|-----------|-----|-------------|----------|-----|------|
| `string` | `xs:string` | `"type": "string"` | `string` | `VARCHAR` | `"type": "string"` |
| `integer` | `xs:integer` | `"type": "integer"` | `int32/int64` | `INTEGER` | `"type": "int/long"` |
| `number` | `xs:decimal` | `"type": "number"` | `double` | `DECIMAL` | `"type": "double"` |
| `boolean` | `xs:boolean` | `"type": "boolean"` | `bool` | `BOOLEAN` | `"type": "boolean"` |
| `date` | `xs:date` | `"format": "date"` | - | `DATE` | `{"type": "int", "logicalType": "date"}` |
| `datetime` | `xs:dateTime` | `"format": "date-time"` | - | `TIMESTAMP` | `{"type": "long", "logicalType": "timestamp-millis"}` |
| `time` | `xs:time` | `"format": "time"` | - | `TIME` | `{"type": "int", "logicalType": "time-millis"}` |
| `binary` | `xs:base64Binary` | `"contentEncoding": "base64"` | `bytes` | `BLOB` | `"type": "bytes"` |
| `uri` | `xs:anyURI` | `"format": "uri"` | `string` | `VARCHAR` | `"type": "string"` |

---

## Complete Examples

### Example 1: CSV to XSD (USDL)

**Input CSV:**
```csv
fieldName,dataType,required,documentation
customerId,string,true,Unique customer identifier
email,string,true,Contact email address
age,integer,false,Customer age
```

**Transformation:**
```utlx
%utlx 1.0
input csv
output xsd %usdl 1.0
---
{
  %namespace: "http://example.com/customer",
  %version: "1.0",

  %types: {
    Customer: {
      %kind: "structure",
      %documentation: "Customer information",

      %fields: map($input, f => {
        %name: f.fieldName,
        %type: f.dataType,
        %required: f.required == true,
        %description: f.documentation
      })
    }
  }
}
```

**Output XSD:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://example.com/customer"
           version="1.0">
  <xs:complexType name="Customer">
    <xs:annotation>
      <xs:documentation>Customer information</xs:documentation>
    </xs:annotation>
    <xs:sequence>
      <xs:element name="customerId" type="xs:string">
        <xs:annotation><xs:documentation>Unique customer identifier</xs:documentation></xs:annotation>
      </xs:element>
      <xs:element name="email" type="xs:string">
        <xs:annotation><xs:documentation>Contact email address</xs:documentation></xs:annotation>
      </xs:element>
      <xs:element name="age" type="xs:integer" minOccurs="0">
        <xs:annotation><xs:documentation>Customer age</xs:documentation></xs:annotation>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

---

### Example 2: Same USDL, JSON Schema Output

**Change line 3 only:**
```utlx
output jsch %usdl 1.0  // ← Changed from 'xsd'
```

**Output JSON Schema:**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "http://example.com/customer",
  "version": "1.0",
  "$defs": {
    "Customer": {
      "type": "object",
      "description": "Customer information",
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
          "description": "Customer age"
        }
      },
      "required": ["customerId", "email"]
    }
  }
}
```

---

### Example 3: Protobuf with Field Numbers

```utlx
%utlx 1.0
output proto %usdl 1.0
---
{
  %types: {
    Customer: {
      %kind: "structure",
      %fields: [
        {
          %name: "customerId",
          %type: "string",
          %fieldNumber: 1
        },
        {
          %name: "email",
          %type: "string",
          %fieldNumber: 2
        },
        {
          %name: "tags",
          %type: "string",
          %array: true,
          %packed: true,
          %fieldNumber: 3
        }
      ]
    }
  }
}
```

**Output Protobuf:**
```protobuf
syntax = "proto3";

message Customer {
  string customer_id = 1;
  string email = 2;
  repeated string tags = 3 [packed=true];
}
```

---

### Example 4: SQL DDL with Constraints

```utlx
%utlx 1.0
output sql %usdl 1.0
---
{
  %types: {
    Customer: {
      %kind: "structure",
      %table: "customers",
      %fields: [
        {
          %name: "id",
          %type: "integer",
          %key: true,
          %autoIncrement: true
        },
        {
          %name: "email",
          %type: "string",
          %required: true,
          %unique: true,
          %constraints: {%maxLength: 255}
        },
        {
          %name: "countryId",
          %type: "integer",
          %required: true,
          %foreignKey: {
            %references: "countries.id",
            %onDelete: "CASCADE"
          }
        }
      ]
    }
  }
}
```

**Output SQL:**
```sql
CREATE TABLE customers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email VARCHAR(255) NOT NULL UNIQUE,
  country_id INTEGER NOT NULL,
  FOREIGN KEY (country_id) REFERENCES countries(id) ON DELETE CASCADE
);
```

---

## Validation Rules

### Unknown Directives (Typos)

**Error** if directive is not in USDL 1.0 catalog:

```utlx
{
  %namepsace: "http://example.com"  // ❌ Typo!
}
```

**Error message:**
```
Error: Unknown USDL directive '%namepsace' at line 3
Did you mean '%namespace'?

Valid USDL 1.0 directives: %namespace, %version, %types, ...
```

### Unsupported Directives (Graceful Degradation)

**Warning** if directive is valid but not supported by target format:

```utlx
%utlx 1.0
output xsd %usdl 1.0
---
{
  %types: {
    Customer: {
      %fields: [
        {
          %name: "id",
          %type: "string",
          %fieldNumber: 1  // ⚠️ XSD doesn't support this
        }
      ]
    }
  }
}
```

**Warning message:**
```
Warning: Directive %fieldNumber is not supported by XSD output format (ignored)
  at line 11, column 11

Output generated successfully.
```

### Required Directives

**Error** if required directive is missing:

```utlx
{
  %namespace: "http://example.com",
  // Missing %types!
}
```

**Error message:**
```
Error: USDL 1.0 requires %types property
Expected structure:
  {
    %types: {
      TypeName: {%kind: "structure", ...}
    }
  }
```

---

## Supported Schema Languages

### Current (USDL 1.0)

1. **XSD (XML Schema Definition)** - Full support (Tier 1+2)
2. **JSON Schema** - Full support (Tier 1+2)

### Planned (Future Releases)

3. **Protobuf** - Tier 1+2+3 (Binary Serialization)
4. **SQL DDL** - Tier 1+2+3 (Database)
5. **Apache Avro** - Tier 1+2+3 (Big Data)
6. **GraphQL** - Tier 1+2+3 (GraphQL)
7. **OData** - Tier 1+2+3 (REST/OData)
8. **OpenAPI** - Tier 1+2+3 (OpenAPI)
9. **AsyncAPI** - Tier 1+2+3 (AsyncAPI)
10. **Apache Thrift** - Tier 1+2+3 (Binary Serialization)
11. **Parquet** - Tier 1+2+3 (Big Data)

### Specialized (Low Priority)

12. **Cap'n Proto** - High-performance RPC
13. **FlatBuffers** - Game development
14. **ASN.1** - Telecommunications

---

## Versioning Guarantee

**USDL 1.0 directive namespace is STABLE and FROZEN.**

- New directives only in USDL 2.0 (if ever needed)
- Serializers can add support for existing directives without version bump
- Transformations written today work with future serializers
- No migration required when new serializers are added

**Example forward compatibility:**
```utlx
// Written in 2025 with USDL 1.0
%utlx 1.0
output proto %usdl 1.0  // ProtoSerializer doesn't exist yet
---
{
  %types: {
    Customer: {
      %fields: [
        {%name: "id", %fieldNumber: 1}  // ← Already valid USDL 1.0
      ]
    }
  }
}

// Still works in 2026 when ProtoSerializer is added
// No changes needed!
```

---

## References

- [USDL Syntax Rationale](../design/usdl-syntax-rationale.md) - Design decisions
- [UTL-X Language Guide](./quick-reference.md) - Core language
- [UDM Specification](../architecture/udm-specification.md) - Universal Data Model
- [XSD W3C Specification](https://www.w3.org/TR/xmlschema11-1/)
- [JSON Schema 2020-12](https://json-schema.org/draft/2020-12/schema)
- [Protocol Buffers Language Guide](https://protobuf.dev/programming-guides/proto3/)
- [Apache Avro Specification](https://avro.apache.org/docs/current/spec.html)

---

**Status:** Ready for implementation
**Next Steps:** Implement parser validation, update serializers for % directive syntax

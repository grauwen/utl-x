# Universal Schema DSL (USDL)

**Version:** 1.0 Extended (REST & Messaging API Support)
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

### USDL 1.0 Extended - API Support

**New in v1.0 Extended:** USDL now includes API directives as **Tier 2 Common** to support both REST and event-driven architectures:

**REST API Directives (14 directives):**
- **OpenAPI** specifications (3.x)
- **RAML** RESTful API Modeling Language
- **API Blueprint** Markdown-based API documentation
- **GraphQL** queries and mutations
- HTTP operations, paths, parameters, request/response bodies, security

**Messaging/Event-Driven API Directives (16 directives):**
- **AsyncAPI** specifications (2.x and 3.0)
- **OpenAPI** webhook/callback definitions
- **gRPC** service definitions and streaming
- **GraphQL** subscription operations
- **Event-driven patterns** using Kafka, AMQP, MQTT, WebSocket

The directives are recognized and validated in USDL 1.0, though full OpenAPI and AsyncAPI serializer implementations are planned for a future release. This approach allows transformation logic to be written now and remain compatible when serializers are added.

### Supported Schema Languages (Current + Future)

**Tier 1 (Implemented):** XSD, JSON Schema
**Tier 2 (Planned):** Protobuf, SQL DDL, Apache Avro, GraphQL, OData, OpenAPI, RAML, AsyncAPI
**Tier 3 (Future):** Apache Thrift, Parquet, API Blueprint
**Tier 4 (Specialized):** Cap'n Proto, FlatBuffers, ASN.1

**Note:** USDL 1.0 now includes:
- **REST API directives** for OpenAPI, RAML, API Blueprint, GraphQL (queries/mutations)
- **Messaging directives** for AsyncAPI, OpenAPI webhooks, gRPC, GraphQL subscriptions
- Cross-format support for event-driven architectures (Kafka, AMQP, MQTT, WebSocket)

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

USDL 1.0 defines **110+ directives** organized into **4 tiers** (including REST API and messaging support added in v1.0 extended):

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

#### Messaging & Event-Driven APIs

**USDL 1.0 Extended:** Support for AsyncAPI, OpenAPI webhooks, gRPC, and GraphQL subscriptions.

| Directive | Scope | Type | Description |
|-----------|-------|------|-------------|
| `%servers` | Top-level | Object | Server/endpoint definitions for messaging or API specs |
| `%channels` | Top-level | Object | Channel/topic definitions (Kafka, AMQP, MQTT, WebSocket) |
| `%operations` | Top-level | Object | API operations (publish/subscribe, send/receive, RPC methods) |
| `%messages` | Top-level | Object | Message definitions for event-driven APIs |
| `%host` | Server | String | Server host (hostname:port) |
| `%protocol` | Server, Channel | String | Communication protocol (kafka, amqp, mqtt, websocket, http, grpc) |
| `%address` | Channel | String | Channel address/topic name (AsyncAPI 3.0) |
| `%subscribe` | Channel | Object | Subscribe operation definition (AsyncAPI 2.x) |
| `%publish` | Channel | Object | Publish operation definition (AsyncAPI 2.x) |
| `%bindings` | Channel, Operation, Message | Object | Protocol-specific bindings (Kafka, AMQP, MQTT configurations) |
| `%contentType` | Message | String | Message content type (application/json, avro/binary, text/plain) |
| `%headers` | Message | Object/String | Message headers schema or type reference |
| `%payload` | Message | Object/String | Message payload schema or type reference |
| `%action` | Operation | String | Operation action type (send, receive, publish, subscribe) |
| `%channel` | Operation | String | Channel reference for this operation |
| `%message` | Operation | String/Array | Message type reference(s) for this operation |
| `%example` | Type, Field, Top-level | Any | Example value or data (moved from Reserved to Common) |

**Supported Formats:**
- **AsyncAPI** - Full messaging specification support
- **OpenAPI** - Webhook callbacks support
- **gRPC** - Service definitions and streaming
- **GraphQL** - Subscription operations

#### REST API Directives

**USDL 1.0 Extended:** Support for REST API specifications (OpenAPI, RAML, API Blueprint).

| Directive | Scope | Type | Description |
|-----------|-------|------|-------------|
| `%paths` | Top-level | Object | API path definitions for REST APIs |
| `%tags` | Top-level, Operation | Array | Tags for grouping operations (moved from Reserved to Common) |
| `%path` | Path | String | API path string (e.g., '/users/{id}') |
| `%method` | Operation, Path | String | HTTP method (GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD, TRACE) |
| `%operationId` | Operation | String | Unique operation identifier for code generation |
| `%summary` | Operation, Path | String | Short operation summary (one-line description) |
| `%requestBody` | Operation | Object/String | Request body schema or type reference |
| `%responses` | Operation | Object | Response definitions keyed by status code |
| `%statusCode` | Response | Integer/String | HTTP status code (200, 404, 500, 'default') |
| `%schema` | Response, Parameter | Object/String | Schema definition or type reference |
| `%parameters` | Operation, Path | Array | Parameter definitions (query, path, header, cookie) |
| `%in` | Parameter | String | Parameter location: 'query', 'path', 'header', 'cookie' |
| `%security` | Top-level, Operation | Array/Object | Security requirements (API keys, OAuth, JWT) |
| `%securitySchemes` | Top-level | Object | Security scheme definitions (apiKey, http, oauth2, openIdConnect) |

**Supported Formats:**
- **OpenAPI** - Full REST API specification support (3.x)
- **RAML** - RESTful API Modeling Language
- **API Blueprint** - Markdown-based API documentation
- **GraphQL** - Query and mutation operations

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

**Note:** `%example` was moved from Reserved to Tier 2 Common in USDL 1.0 to support messaging specifications.

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

### Example 5: AsyncAPI Event-Driven API

```utlx
%utlx 1.0
output asyncapi %usdl 1.0
---
{
  %namespace: "urn:com:example:orders",
  %version: "1.0.0",

  %servers: {
    production: {
      %host: "kafka.example.com:9092",
      %protocol: "kafka",
      %description: "Production Kafka cluster"
    }
  },

  %channels: {
    orderEvents: {
      %address: "orders.events",
      %description: "Order lifecycle events",
      %subscribe: {
        %message: "OrderCreated"
      },
      %bindings: {
        kafka: {
          topic: "orders.events",
          partitions: 3,
          replicas: 2
        }
      }
    }
  },

  %messages: {
    OrderCreated: {
      %contentType: "application/json",
      %description: "Published when a new order is created",
      %headers: {
        %kind: "structure",
        %fields: [
          {%name: "correlationId", %type: "string", %required: true},
          {%name: "timestamp", %type: "datetime", %required: true}
        ]
      },
      %payload: "Order",
      %example: {
        headers: {
          correlationId: "abc-123",
          timestamp: "2025-10-27T10:00:00Z"
        },
        payload: {
          orderId: "ORD-001",
          customerId: "CUST-456",
          total: 299.99
        }
      }
    }
  },

  %types: {
    Order: {
      %kind: "structure",
      %documentation: "Order payload structure",
      %fields: [
        {%name: "orderId", %type: "string", %required: true},
        {%name: "customerId", %type: "string", %required: true},
        {%name: "total", %type: "number", %required: true},
        {%name: "items", %type: "OrderItem", %array: true}
      ]
    },
    OrderItem: {
      %kind: "structure",
      %fields: [
        {%name: "sku", %type: "string", %required: true},
        {%name: "quantity", %type: "integer", %required: true},
        {%name: "price", %type: "number", %required: true}
      ]
    }
  }
}
```

**Output AsyncAPI 3.0:**
```yaml
asyncapi: 3.0.0
info:
  title: Orders API
  version: 1.0.0

servers:
  production:
    host: kafka.example.com:9092
    protocol: kafka
    description: Production Kafka cluster

channels:
  orderEvents:
    address: orders.events
    description: Order lifecycle events
    messages:
      OrderCreated:
        $ref: '#/components/messages/OrderCreated'
    bindings:
      kafka:
        topic: orders.events
        partitions: 3
        replicas: 2

operations:
  subscribeOrderEvents:
    action: subscribe
    channel:
      $ref: '#/channels/orderEvents'
    messages:
      - $ref: '#/components/messages/OrderCreated'

components:
  messages:
    OrderCreated:
      contentType: application/json
      description: Published when a new order is created
      headers:
        type: object
        properties:
          correlationId:
            type: string
          timestamp:
            type: string
            format: date-time
        required:
          - correlationId
          - timestamp
      payload:
        $ref: '#/components/schemas/Order'
      examples:
        - headers:
            correlationId: abc-123
            timestamp: '2025-10-27T10:00:00Z'
          payload:
            orderId: ORD-001
            customerId: CUST-456
            total: 299.99

  schemas:
    Order:
      type: object
      description: Order payload structure
      properties:
        orderId:
          type: string
        customerId:
          type: string
        total:
          type: number
        items:
          type: array
          items:
            $ref: '#/components/schemas/OrderItem'
      required:
        - orderId
        - customerId
        - total

    OrderItem:
      type: object
      properties:
        sku:
          type: string
        quantity:
          type: integer
        price:
          type: number
      required:
        - sku
        - quantity
        - price
```

---

### Example 6: OpenAPI REST API

```utlx
%utlx 1.0
output openapi %usdl 1.0
---
{
  %namespace: "https://api.example.com/v1",
  %version: "1.0.0",

  %servers: [
    {
      %host: "api.example.com",
      %description: "Production API server"
    }
  ],

  %paths: {
    "/orders": {
      get: {
        %operationId: "listOrders",
        %summary: "List all orders",
        %tags: ["Orders"],
        %parameters: [
          {
            %name: "status",
            %in: "query",
            %schema: {%kind: "primitive", %baseType: "string"},
            %description: "Filter by order status"
          },
          {
            %name: "limit",
            %in: "query",
            %schema: {%kind: "primitive", %baseType: "integer"},
            %description: "Maximum number of results"
          }
        ],
        %responses: {
          "200": {
            %description: "Successful response",
            %schema: {
              %kind: "structure",
              %fields: [
                {%name: "orders", %type: "Order", %array: true}
              ]
            }
          },
          "400": {
            %description: "Bad request",
            %schema: "Error"
          }
        },
        %security: [{"apiKey": []}]
      },
      post: {
        %operationId: "createOrder",
        %summary: "Create a new order",
        %tags: ["Orders"],
        %requestBody: {
          %required: true,
          %contentType: "application/json",
          %schema: "Order"
        },
        %responses: {
          "201": {
            %description: "Order created",
            %schema: "Order"
          },
          "400": {
            %description: "Invalid input",
            %schema: "Error"
          }
        },
        %security: [{"apiKey": []}]
      }
    },
    "/orders/{orderId}": {
      get: {
        %operationId: "getOrder",
        %summary: "Get order by ID",
        %tags: ["Orders"],
        %parameters: [
          {
            %name: "orderId",
            %in: "path",
            %required: true,
            %schema: {%kind: "primitive", %baseType: "string"},
            %description: "Order identifier"
          }
        ],
        %responses: {
          "200": {
            %description: "Order found",
            %schema: "Order"
          },
          "404": {
            %description: "Order not found",
            %schema: "Error"
          }
        }
      }
    }
  },

  %securitySchemes: {
    apiKey: {
      type: "apiKey",
      %in: "header",
      %name: "X-API-Key"
    }
  },

  %types: {
    Order: {
      %kind: "structure",
      %documentation: "Order information",
      %fields: [
        {%name: "orderId", %type: "string", %required: true},
        {%name: "customerId", %type: "string", %required: true},
        {%name: "status", %type: "string", %required: true},
        {%name: "total", %type: "number", %required: true},
        {%name: "items", %type: "OrderItem", %array: true}
      ]
    },
    OrderItem: {
      %kind: "structure",
      %fields: [
        {%name: "sku", %type: "string", %required: true},
        {%name: "quantity", %type: "integer", %required: true},
        {%name: "price", %type: "number", %required: true}
      ]
    },
    Error: {
      %kind: "structure",
      %fields: [
        {%name: "code", %type: "string", %required: true},
        {%name: "message", %type: "string", %required: true}
      ]
    }
  }
}
```

**Output OpenAPI 3.0:**
```yaml
openapi: 3.0.0
info:
  title: Orders API
  version: 1.0.0
servers:
  - url: https://api.example.com
    description: Production API server

paths:
  /orders:
    get:
      operationId: listOrders
      summary: List all orders
      tags:
        - Orders
      parameters:
        - name: status
          in: query
          schema:
            type: string
          description: Filter by order status
        - name: limit
          in: query
          schema:
            type: integer
          description: Maximum number of results
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                type: object
                properties:
                  orders:
                    type: array
                    items:
                      $ref: '#/components/schemas/Order'
        '400':
          description: Bad request
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'
      security:
        - apiKey: []

    post:
      operationId: createOrder
      summary: Create a new order
      tags:
        - Orders
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Order'
      responses:
        '201':
          description: Order created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Order'
        '400':
          description: Invalid input
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'
      security:
        - apiKey: []

  /orders/{orderId}:
    get:
      operationId: getOrder
      summary: Get order by ID
      tags:
        - Orders
      parameters:
        - name: orderId
          in: path
          required: true
          schema:
            type: string
          description: Order identifier
      responses:
        '200':
          description: Order found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Order'
        '404':
          description: Order not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'

components:
  securitySchemes:
    apiKey:
      type: apiKey
      in: header
      name: X-API-Key

  schemas:
    Order:
      type: object
      description: Order information
      properties:
        orderId:
          type: string
        customerId:
          type: string
        status:
          type: string
        total:
          type: number
        items:
          type: array
          items:
            $ref: '#/components/schemas/OrderItem'
      required:
        - orderId
        - customerId
        - status
        - total

    OrderItem:
      type: object
      properties:
        sku:
          type: string
        quantity:
          type: integer
        price:
          type: number
      required:
        - sku
        - quantity
        - price

    Error:
      type: object
      properties:
        code:
          type: string
        message:
          type: string
      required:
        - code
        - message
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
3. **OpenAPI** - Directive support (Tier 1+2), serializer planned (REST API)
4. **AsyncAPI** - Directive support (Tier 1+2), serializer planned (Event-Driven/Messaging)
5. **RAML** - Directive support (Tier 1+2), serializer planned (REST API)

### Planned (Future Releases)

6. **Protobuf** - Tier 1+2+3 (Binary Serialization)
7. **SQL DDL** - Tier 1+2+3 (Database)
8. **Apache Avro** - Tier 1+2+3 (Big Data)
9. **GraphQL** - Tier 1+2+3 (GraphQL schema definitions)
10. **OData** - Tier 1+2+3 (REST/OData)
11. **API Blueprint** - Tier 1+2 (REST API documentation)
12. **Apache Thrift** - Tier 1+2+3 (Binary Serialization)
13. **Parquet** - Tier 1+2+3 (Big Data)

### Specialized (Low Priority)

14. **Cap'n Proto** - High-performance RPC
15. **FlatBuffers** - Game development
16. **ASN.1** - Telecommunications

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

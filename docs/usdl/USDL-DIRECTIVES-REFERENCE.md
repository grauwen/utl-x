# USDL 1.0 Directives Reference

**Version:** 1.0
**Status:** Official Specification
**Last Updated:** 2025-11-16
**Purpose:** Central registry of all USDL directives for IDE, MCP, and REST API integration

---

## Table of Contents

1. [Overview](#overview)
2. [Directive Organization](#directive-organization)
3. [Tier 1: Core Directives](#tier-1-core-directives-9-directives)
4. [Tier 2: Common Directives](#tier-2-common-directives-51-directives)
5. [Tier 3: Format-Specific Directives](#tier-3-format-specific-directives-44-directives)
6. [Tier 4: Reserved Directives](#tier-4-reserved-directives-15-directives)
7. [Format Compatibility Matrix](#format-compatibility-matrix)
8. [Understanding Coverage Percentages](#understanding-coverage-percentages)
9. [Quick Reference by Use Case](#quick-reference-by-use-case)
10. [REST API Integration](#rest-api-integration)

---

## Overview

USDL (Universal Schema Definition Language) is a format-agnostic schema definition language that enables schema transformations across multiple formats using a unified `%` directive syntax.

### Key Principles

- **Format Abstraction**: Define schema semantics once, generate multiple formats
- **Explicit Directives**: All USDL keywords use `%` prefix (e.g., `%namespace`, `%types`, `%kind`)
- **Versioned**: USDL 1.0 namespace is frozen - no breaking changes
- **Tiered Implementation**: Implement incrementally by tier (1 → 2 → 3 → 4)

### Total Directive Count

- **Tier 1 (Core)**: 9 directives - Required for basic schemas
- **Tier 2 (Common)**: 51 directives - Recommended, 80%+ format support
- **Tier 3 (Format-Specific)**: 44 directives - Specialized needs
- **Tier 4 (Reserved)**: 15 directives - Future USDL versions
- **TOTAL**: **119 directives**

---

## Directive Organization

### By Tier

```
Tier 1 (Core)           → Universal directives for all schemas
Tier 2 (Common)         → Recommended directives (80%+ support)
Tier 3 (Format-Specific)→ Specialized format directives
Tier 4 (Reserved)       → Future USDL 2.0+ features
```

### By Scope

```
TOP_LEVEL              → Root schema level (%namespace, %types, %version)
TYPE_DEFINITION        → Within type definitions (%kind, %fields, %documentation)
FIELD_DEFINITION       → Within field definitions (%name, %type, %required)
CONSTRAINT             → Within constraints object (%pattern, %minimum, %maximum)
ENUMERATION            → Within enumeration values (%value, %description)
CHANNEL_DEFINITION     → Within messaging channels (AsyncAPI, Kafka, AMQP)
OPERATION_DEFINITION   → Within API operations (REST, GraphQL, AsyncAPI)
SERVER_DEFINITION      → Within server/endpoint definitions
MESSAGE_DEFINITION     → Within message definitions (event-driven APIs)
PATH_DEFINITION        → Within API path definitions (OpenAPI, REST)
PARAMETER_DEFINITION   → Within parameter definitions (query, path, header)
RESPONSE_DEFINITION    → Within response definitions (status codes, schemas)
```

---

## Tier 1: Core Directives (9 directives)

**Purpose**: Universal directives required for all schema languages

### Top-Level Directives

| Directive | Scope | Type | Required | Description |
|-----------|-------|------|----------|-------------|
| `%namespace` | TOP_LEVEL | String | No | Schema namespace or package name |
| `%version` | TOP_LEVEL | String | No | Schema version |
| `%types` | TOP_LEVEL | Object | **Yes** | Type definitions (at least one required) |

### Type Definition Directives

| Directive | Scope | Type | Required | Description |
|-----------|-------|------|----------|-------------|
| `%kind` | TYPE_DEFINITION | String | **Yes** | Type kind: `structure`, `enumeration`, `primitive`, `array`, `union`, `interface` |
| `%documentation` | TYPE_DEFINITION | String | No | Type-level documentation |

### Field Definition Directives

| Directive | Scope | Type | Required | Description |
|-----------|-------|------|----------|-------------|
| `%name` | FIELD_DEFINITION | String | **Yes** | Field or element name |
| `%type` | FIELD_DEFINITION | String | **Yes** | Field type (primitive or type reference) |
| `%description` | FIELD_DEFINITION, ENUMERATION | String | No | Field-level or value-level description |

### Enumeration Directives

| Directive | Scope | Type | Required | Description |
|-----------|-------|------|----------|-------------|
| `%value` | ENUMERATION | String or Number | No | Enumeration value (when using object form with description) |

### Example: Basic Schema

```utlx
%utlx 1.0
output xsd %usdl 1.0
---
{
  %namespace: "http://example.com/customer",
  %version: "1.0",
  %types: {
    Customer: {
      %kind: "structure",
      %documentation: "Customer entity",
      %fields: [
        {%name: "id", %type: "string"},
        {%name: "name", %type: "string"}
      ]
    }
  }
}
```

---

## Tier 2: Common Directives (51 directives)

**Purpose**: Recommended directives supported by 80%+ schema languages

### Structure & Type Directives (5)

| Directive | Scope | Type | Description |
|-----------|-------|------|-------------|
| `%fields` | TYPE_DEFINITION | Array | Array of field definitions (for structures) |
| `%values` | TYPE_DEFINITION | Array | Enumeration values |
| `%itemType` | TYPE_DEFINITION | String | Element type for arrays |
| `%baseType` | TYPE_DEFINITION | String | Base type for primitives or restrictions |
| `%options` | TYPE_DEFINITION | Array | Type references for union types |

### Field Modifiers (5)

| Directive | Scope | Type | Description |
|-----------|-------|------|-------------|
| `%required` | FIELD_DEFINITION | Boolean | Is this field required? |
| `%array` | FIELD_DEFINITION | Boolean | Is this field an array/repeated? |
| `%nullable` | FIELD_DEFINITION | Boolean | Can this field be null? |
| `%default` | FIELD_DEFINITION | Any | Default value for field |
| `%constraints` | FIELD_DEFINITION, TYPE_DEFINITION | Object | Constraint definitions |

### Constraint Directives (10)

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%minLength` | CONSTRAINT | Integer | Minimum string length | XSD, JSON Schema, SQL |
| `%maxLength` | CONSTRAINT | Integer | Maximum string length | XSD, JSON Schema, SQL |
| `%pattern` | CONSTRAINT | String | Regex pattern for string validation | XSD, JSON Schema, SQL |
| `%minimum` | CONSTRAINT | Number | Minimum value (inclusive) | XSD, JSON Schema, SQL |
| `%maximum` | CONSTRAINT | Number | Maximum value (inclusive) | XSD, JSON Schema, SQL |
| `%exclusiveMinimum` | CONSTRAINT | Number | Minimum value (exclusive) | XSD, JSON Schema |
| `%exclusiveMaximum` | CONSTRAINT | Number | Maximum value (exclusive) | XSD, JSON Schema |
| `%enum` | CONSTRAINT | Array | Allowed values enumeration | All |
| `%format` | CONSTRAINT | String | Format hint: `email`, `uri`, `date`, `date-time` | JSON Schema, OpenAPI |
| `%multipleOf` | CONSTRAINT | Number | Value must be a multiple of this number | JSON Schema |

### Messaging & Event-Driven API Directives (13)

**Supports**: AsyncAPI, Kafka, AMQP, MQTT, WebSocket, gRPC, GraphQL subscriptions

#### Root-Level Messaging

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%servers` | TOP_LEVEL | Object | Server/endpoint definitions | AsyncAPI, OpenAPI, gRPC |
| `%channels` | TOP_LEVEL | Object | Channel/topic definitions | AsyncAPI, gRPC |
| `%operations` | TOP_LEVEL | Object | API operations (publish/subscribe, RPC) | AsyncAPI, gRPC, GraphQL |
| `%messages` | TOP_LEVEL | Object | Message definitions | AsyncAPI, gRPC |

#### Server Directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%host` | SERVER_DEFINITION | String | Server host (hostname:port) | AsyncAPI, OpenAPI, gRPC |
| `%protocol` | SERVER_DEFINITION, CHANNEL_DEFINITION | String | Protocol: `kafka`, `amqp`, `mqtt`, `websocket`, `http`, `grpc` | AsyncAPI, gRPC |

#### Channel Directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%address` | CHANNEL_DEFINITION | String | Channel address/topic name | AsyncAPI 3.0 |
| `%subscribe` | CHANNEL_DEFINITION | Object | Subscribe operation definition | AsyncAPI 2.x |
| `%publish` | CHANNEL_DEFINITION | Object | Publish operation definition | AsyncAPI 2.x |
| `%bindings` | CHANNEL_DEFINITION, OPERATION_DEFINITION, MESSAGE_DEFINITION | Object | Protocol-specific bindings (Kafka, AMQP, MQTT) | AsyncAPI |

#### Message Directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%contentType` | MESSAGE_DEFINITION | String | Message content type (`application/json`, `avro/binary`) | AsyncAPI, OpenAPI |
| `%headers` | MESSAGE_DEFINITION | Object or String | Message headers schema or type reference | AsyncAPI, OpenAPI |
| `%payload` | MESSAGE_DEFINITION | Object or String | Message payload schema or type reference | AsyncAPI, OpenAPI |

#### Operation Directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%action` | OPERATION_DEFINITION | String | Operation action: `send`, `receive`, `publish`, `subscribe` | AsyncAPI |
| `%channel` | OPERATION_DEFINITION | String | Channel reference for this operation | AsyncAPI |
| `%message` | OPERATION_DEFINITION | String or Array | Message type reference(s) | AsyncAPI |
| `%example` | TOP_LEVEL, TYPE_DEFINITION, FIELD_DEFINITION | Any | Example value or data | JSON Schema, OpenAPI, AsyncAPI, Avro |

### REST API Directives (18)

**Supports**: OpenAPI, RAML, API Blueprint

#### Root-Level REST API

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%paths` | TOP_LEVEL | Object | API path definitions | OpenAPI, RAML, API Blueprint |
| `%tags` | TOP_LEVEL, OPERATION_DEFINITION | Array | Tags for grouping operations | OpenAPI, AsyncAPI, RAML |

#### Path & Operation Directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%path` | PATH_DEFINITION | String | API path string (e.g., `/users/{id}`) | OpenAPI, RAML, GraphQL |
| `%method` | OPERATION_DEFINITION, PATH_DEFINITION | String | HTTP method: `GET`, `POST`, `PUT`, `DELETE`, `PATCH` | OpenAPI, RAML, API Blueprint |
| `%operationId` | OPERATION_DEFINITION | String | Unique operation identifier | OpenAPI, RAML, AsyncAPI |
| `%summary` | OPERATION_DEFINITION, PATH_DEFINITION | String | Short operation summary (one-line) | OpenAPI, RAML, AsyncAPI |

#### Request/Response Directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%requestBody` | OPERATION_DEFINITION | Object or String | Request body schema or type reference | OpenAPI, RAML, API Blueprint |
| `%responses` | OPERATION_DEFINITION | Object | Response definitions keyed by status code | OpenAPI, RAML, API Blueprint |
| `%statusCode` | RESPONSE_DEFINITION | Integer or String | HTTP status code: `200`, `404`, `500`, `default` | OpenAPI, RAML, API Blueprint |
| `%apiSchema` | RESPONSE_DEFINITION, PARAMETER_DEFINITION | Object or String | Schema definition or type reference for API response/parameter | OpenAPI, RAML, API Blueprint |

#### Parameter Directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%parameters` | OPERATION_DEFINITION, PATH_DEFINITION | Array | Parameter definitions (query, path, header, cookie) | OpenAPI, RAML, GraphQL |
| `%in` | PARAMETER_DEFINITION | String | Parameter location: `query`, `path`, `header`, `cookie` | OpenAPI, RAML |

#### Security Directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%security` | TOP_LEVEL, OPERATION_DEFINITION | Array or Object | Security requirements (API keys, OAuth, JWT) | OpenAPI, AsyncAPI, RAML, GraphQL |
| `%securitySchemes` | TOP_LEVEL | Object | Security scheme definitions (`apiKey`, `http`, `oauth2`, `openIdConnect`) | OpenAPI, AsyncAPI, RAML |

### Example: REST API Schema

```utlx
%utlx 1.0
output openapi %usdl 1.0
---
{
  %namespace: "http://api.example.com/v1",
  %version: "1.0.0",

  %types: {
    Customer: {
      %kind: "structure",
      %fields: [
        {%name: "id", %type: "string", %required: true},
        {%name: "email", %type: "string", %required: true,
         %constraints: {%format: "email"}},
        {%name: "status", %type: "CustomerStatus"}
      ]
    },
    CustomerStatus: {
      %kind: "enumeration",
      %values: ["active", "inactive", "suspended"]
    }
  },

  %paths: {
    "/customers/{id}": {
      %method: "GET",
      %operationId: "getCustomer",
      %summary: "Retrieve customer by ID",
      %parameters: [
        {%name: "id", %in: "path", %type: "string", %required: true}
      ],
      %responses: {
        "200": {%apiSchema: "Customer"}
      }
    }
  }
}
```

---

## Tier 3: Format-Specific Directives (44 directives)

**Purpose**: Specialized directives for specific schema formats

### Binary Serialization (Protobuf, Thrift) - 7 directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%fieldNumber` | FIELD_DEFINITION | Integer | Field tag number (1-536,870,911) | Protobuf, Thrift |
| `%fieldId` | FIELD_DEFINITION | Integer | Field identifier (alternative) | Cap'n Proto, FlatBuffers |
| `%ordinal` | ENUMERATION | Integer | Enum value ordinal | Protobuf, Thrift, Avro |
| `%packed` | FIELD_DEFINITION | Boolean | Packed encoding for repeated numeric fields | Protobuf |
| `%reserved` | TYPE_DEFINITION | Array | Reserved field numbers or names | Protobuf, Thrift |
| `%oneof` | FIELD_DEFINITION | String | Oneof group name (mutually exclusive fields) | Protobuf |
| `%map` | FIELD_DEFINITION | Boolean | Is this a map/dictionary field? | Protobuf, Thrift, Avro |

### Big Data (Avro, Parquet) - 8 directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%logicalType` | FIELD_DEFINITION | String | Semantic type: `date`, `timestamp-millis`, `decimal` | Avro, Parquet |
| `%aliases` | TYPE_DEFINITION, FIELD_DEFINITION | Array | Alternate names for schema evolution | Avro |
| `%precision` | FIELD_DEFINITION, CONSTRAINT | Integer | Decimal precision | Avro, SQL, Parquet |
| `%scale` | FIELD_DEFINITION, CONSTRAINT | Integer | Decimal scale | Avro, SQL, Parquet |
| `%size` | FIELD_DEFINITION | Integer | Fixed size for bytes or strings | Avro, Cap'n Proto |
| `%repetition` | FIELD_DEFINITION | String | Parquet repetition: `required`, `optional`, `repeated` | Parquet |
| `%encoding` | FIELD_DEFINITION | String | Parquet encoding type | Parquet |
| `%compression` | TYPE_DEFINITION | String | Compression algorithm: `snappy`, `gzip`, `lzo` | Avro, Parquet |

### Database (SQL DDL) - 15 directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%table` | TYPE_DEFINITION | String | Database table name | SQL, Avro, Parquet |
| `%dbSchema` | TYPE_DEFINITION | String | Database schema/namespace (`public`, `dbo`) | SQL, Avro, Parquet |
| `%dbColumn` | FIELD_DEFINITION | String | Database column name (if different from field name) | SQL, Avro, Parquet |
| `%primaryKey` | FIELD_DEFINITION, TYPE_DEFINITION | Boolean or Array | Primary key field or composite key | SQL |
| `%key` | FIELD_DEFINITION | Boolean | Is this field a primary key? (deprecated, use `%primaryKey`) | SQL, OData, GraphQL |
| `%autoIncrement` | FIELD_DEFINITION | Boolean | Auto-increment/serial column | SQL |
| `%unique` | FIELD_DEFINITION | Boolean | Unique constraint | SQL |
| `%index` | FIELD_DEFINITION | String or Boolean | Index hint or name | SQL |
| `%foreignKey` | FIELD_DEFINITION | Object or String | Foreign key reference: `{%table, %column, %onDelete, %onUpdate}` | SQL |
| `%references` | FIELD_DEFINITION | String | Referenced column name | SQL |
| `%onDelete` | FIELD_DEFINITION | String | ON DELETE action: `CASCADE`, `SET NULL`, `RESTRICT` | SQL |
| `%onUpdate` | FIELD_DEFINITION | String | ON UPDATE action: `CASCADE`, `SET NULL`, `RESTRICT` | SQL |
| `%check` | FIELD_DEFINITION, TYPE_DEFINITION | String | CHECK constraint expression | SQL |
| `%sqlType` | FIELD_DEFINITION | String | Override SQL data type (`VARCHAR(100)`, `JSONB`, `UUID`) | SQL |
| `%sqlDialect` | TOP_LEVEL | String | Target SQL dialect: `postgresql`, `mysql`, `oracle`, `sqlserver`, `sqlite` | SQL |
| `%engine` | TYPE_DEFINITION | String | Storage engine for MySQL (`InnoDB`, `MyISAM`) | SQL |
| `%charset` | TYPE_DEFINITION | String | Character set for MySQL (`utf8mb4`, `latin1`) | SQL |
| `%collation` | TYPE_DEFINITION | String | Collation (`utf8mb4_unicode_ci`, `en_US.UTF-8`) | SQL |

### OData (REST/Entity Modeling) - 5 directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%entityType` | TYPE_DEFINITION | Boolean | Mark as entity type vs complex type | OData |
| `%navigation` | TYPE_DEFINITION | Array | Navigation properties | OData |
| `%target` | FIELD_DEFINITION | String | Navigation target entity | OData |
| `%cardinality` | FIELD_DEFINITION | String | Relationship cardinality: `1:1`, `1:N`, `N:N` | OData |
| `%referentialConstraint` | FIELD_DEFINITION | Object | Foreign key constraints for navigation | OData |

### GraphQL - 2 directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%implements` | TYPE_DEFINITION | Array | Interfaces implemented by this type | GraphQL, Avro, Thrift |
| `%resolver` | FIELD_DEFINITION | String | Resolver function hint | GraphQL |

### OpenAPI / JSON Schema Extensions - 7 directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%readOnly` | FIELD_DEFINITION | Boolean | Read-only property (API responses only) | JSON Schema, OpenAPI |
| `%writeOnly` | FIELD_DEFINITION | Boolean | Write-only property (API requests only) | JSON Schema, OpenAPI |
| `%discriminator` | TYPE_DEFINITION | Object | Polymorphism discriminator | JSON Schema, OpenAPI |
| `%propertyName` | TYPE_DEFINITION | String | Discriminator property name | JSON Schema, OpenAPI |
| `%mapping` | TYPE_DEFINITION | Object | Discriminator value to schema mapping | JSON Schema, OpenAPI |
| `%externalDocs` | TYPE_DEFINITION, FIELD_DEFINITION | Object | External documentation reference | OpenAPI |
| `%url` | TYPE_DEFINITION | String | External documentation URL | OpenAPI |
| `%examples` | FIELD_DEFINITION, TYPE_DEFINITION | Array | Example values | JSON Schema, OpenAPI |
| `%xml` | FIELD_DEFINITION | Object | XML representation hints | OpenAPI, XSD |

### XSD-Specific - 4 directives

| Directive | Scope | Type | Description | Formats |
|-----------|-------|------|-------------|---------|
| `%elementFormDefault` | TOP_LEVEL | String | `qualified` or `unqualified` | XSD |
| `%attributeFormDefault` | TOP_LEVEL | String | `qualified` or `unqualified` | XSD |
| `%choice` | TYPE_DEFINITION | Boolean | XSD choice compositor | XSD |
| `%all` | TYPE_DEFINITION | Boolean | XSD all compositor (unordered) | XSD |

### Example: Protobuf Schema

```utlx
%utlx 1.0
output proto %usdl 1.0
---
{
  %namespace: "com.example.orders",
  %version: "1.0",
  %types: {
    Order: {
      %kind: "structure",
      %fields: [
        {%name: "order_id", %type: "string", %required: true, %fieldNumber: 1},
        {%name: "customer_id", %type: "string", %required: true, %fieldNumber: 2},
        {%name: "items", %type: "OrderItem", %array: true, %fieldNumber: 3},
        {%name: "total", %type: "double", %fieldNumber: 4}
      ]
    },
    OrderItem: {
      %kind: "structure",
      %fields: [
        {%name: "sku", %type: "string", %fieldNumber: 1},
        {%name: "quantity", %type: "int32", %fieldNumber: 2},
        {%name: "price", %type: "double", %fieldNumber: 3}
      ]
    }
  }
}
```

---

## Tier 4: Reserved Directives (15 directives)

**Purpose**: Reserved for future USDL versions (2.0+)

### Schema Composition - 7 directives

| Directive | Scope | Type | Description |
|-----------|-------|------|-------------|
| `%allOf` | TYPE_DEFINITION | Array | Schema composition: all schemas must match |
| `%anyOf` | TYPE_DEFINITION | Array | Schema composition: any schema may match |
| `%oneOf` | TYPE_DEFINITION | Array | Schema composition: exactly one schema must match |
| `%not` | TYPE_DEFINITION | Object | Schema negation |
| `%if` | TYPE_DEFINITION | Object | Conditional schema: if condition |
| `%then` | TYPE_DEFINITION | Object | Conditional schema: then branch |
| `%else` | TYPE_DEFINITION | Object | Conditional schema: else branch |

### Advanced Metadata - 5 directives

| Directive | Scope | Type | Description |
|-----------|-------|------|-------------|
| `%deprecated` | TYPE_DEFINITION, FIELD_DEFINITION | Boolean | Mark as deprecated |
| `%reason` | TYPE_DEFINITION, FIELD_DEFINITION | String | Deprecation reason |
| `%replacedBy` | TYPE_DEFINITION, FIELD_DEFINITION | String | Replacement type or field |
| `%title` | TYPE_DEFINITION | String | Human-readable title |
| `%comment` | TYPE_DEFINITION, FIELD_DEFINITION | String | Internal comment (not included in output) |

### Advanced References - 3 directives

| Directive | Scope | Type | Description |
|-----------|-------|------|-------------|
| `%ref` | TYPE_DEFINITION, FIELD_DEFINITION | String | Reference to external schema |
| `%extends` | TYPE_DEFINITION | String | Type extension/inheritance |
| `%typedef` | TYPE_DEFINITION | String | Type alias definition |
| `%alignment` | FIELD_DEFINITION | Integer | Memory alignment (Cap'n Proto, FlatBuffers) |
| `%generic` | TYPE_DEFINITION | Array | Generic type parameters |

---

## Format Compatibility Matrix

### Supported Schema Formats

| Format | Abbrev | Tier 1 | Tier 2 | Tier 3 | Overall | Notes |
|--------|--------|--------|--------|--------|---------|-------|
| **XSD** | xsd | 100% | 95% | 40% | **95%** | Best coverage, some XSD-specific directives |
| **JSON Schema** | jsch | 100% | 90% | 45% | **90%** | Excellent coverage, OpenAPI adds more |
| **Protobuf** | proto | 100% | 80% | 60% | **85%** | Needs `%fieldNumber`, limited constraints |
| **Avro** | avro | 100% | 75% | 50% | **80%** | Needs `%logicalType`, `%precision`, `%scale` |
| **SQL DDL** | sql | 100% | 70% | 70% | **75%** | Needs many database-specific directives |
| **GraphQL** | graphql | 100% | 60% | 30% | **70%** | Good type coverage, limited constraints |
| **OpenAPI** | openapi | 100% | 90% | 55% | **85%** | JSON Schema + REST extensions |
| **AsyncAPI** | asyncapi | 100% | 85% | 40% | **80%** | Event-driven, messaging protocols |
| **OData** | odata | 100% | 50% | 50% | **60%** | Entity/navigation focus |
| **Thrift** | thrift | 100% | 70% | 40% | **70%** | Similar to Protobuf |
| **Parquet** | parquet | 100% | 60% | 45% | **65%** | Columnar storage |
| **RAML** | raml | 100% | 80% | 35% | **75%** | REST API modeling |
| **Cap'n Proto** | capnp | 100% | 70% | 50% | **70%** | Zero-copy serialization |
| **FlatBuffers** | flatbuf | 100% | 65% | 45% | **65%** | Game/performance focus |

**Average Compatibility**: 76% across all formats

---

## Understanding Coverage Percentages

### Why Not 100% for Any Format?

**USDL is intentionally broader than any single format.** It covers multiple domains:

```
Domain 1: Data Schemas      → XSD, JSON Schema, Protobuf, Avro
Domain 2: REST APIs         → OpenAPI, RAML, API Blueprint
Domain 3: Event/Messaging   → AsyncAPI, Kafka, AMQP, MQTT
Domain 4: Databases         → SQL DDL
Domain 5: Entity Models     → OData, GraphQL
```

No single format supports all domains, so percentages reflect **how many USDL directives are applicable** to each format.

### XSD at 95% - Detailed Breakdown

#### ✅ **Tier 1 (Core): 100%** - All directives work

```utlx
%namespace  → targetNamespace
%version    → version attribute
%types      → xs:complexType / xs:simpleType
%kind       → structure, enumeration, primitive, array
%name       → element name
%type       → xs:string, xs:integer, etc.
```

#### ⚠️ **Tier 2 (Common): ~85%** - Most work, but not all

**What works**:
```utlx
%fields           ✅ → xs:sequence/xs:element
%required         ✅ → minOccurs="1" (default)
%constraints      ✅ → xs:restriction facets
%pattern          ✅ → xs:pattern
%minimum          ✅ → xs:minInclusive
%maximum          ✅ → xs:maxInclusive
%minLength        ✅ → xs:minLength
%maxLength        ✅ → xs:maxLength
```

**What doesn't apply to XSD** (REST API directives):
```utlx
%paths            ❌ XSD has no HTTP endpoints
%method           ❌ XSD has no GET/POST/PUT
%requestBody      ❌ XSD has no request/response model
%responses        ❌ XSD has no status codes
%parameters       ❌ XSD has no query parameters
%operationId      ❌ XSD has no API operations
%security         ❌ XSD has no authentication schemes
```

**What doesn't apply to XSD** (Messaging directives):
```utlx
%channels         ❌ XSD has no Kafka topics
%operations       ❌ XSD has no pub/sub operations
%protocol         ❌ XSD has no AMQP/MQTT protocols
%payload          ❌ XSD has no message payloads
%subscribe        ❌ XSD has no event subscriptions
%publish          ❌ XSD has no event publishing
%bindings         ❌ XSD has no protocol bindings
```

**What needs translation**:
```utlx
%format: "email"  ⚠️ XSD doesn't have native email type
                     Generates: xs:pattern="[^@]+@[^@]+"
```

#### ⚠️ **Tier 3 (Format-Specific): 40%** - Only XSD-specific directives work

**What works**:
```utlx
%elementFormDefault   ✅ XSD-specific
%attributeFormDefault ✅ XSD-specific
%choice               ✅ XSD compositor
%all                  ✅ XSD compositor
```

**What doesn't apply**:
```utlx
%fieldNumber          ❌ Protobuf-specific
%logicalType          ❌ Avro-specific
%precision            ❌ Avro/SQL-specific
%scale                ❌ Avro/SQL-specific
%primaryKey           ❌ SQL DDL-specific
%foreignKey           ❌ SQL DDL-specific
%autoIncrement        ❌ SQL DDL-specific
%table                ❌ SQL DDL-specific
%entityType           ❌ OData-specific
%navigation           ❌ OData-specific
%resolver             ❌ GraphQL-specific
%implements           ❌ GraphQL/Avro-specific
```

**Result**: XSD uses **95% of applicable directives** (ignores REST/messaging/database/other-format directives)

---

### JSON Schema at 90% - Detailed Breakdown

#### ✅ **Tier 1 (Core): 100%** - Perfect coverage

#### ⚠️ **Tier 2 (Common): ~90%** - Almost perfect

**What works perfectly**:
```utlx
%constraints: {
  %format: "email"        ✅ Native JSON Schema keyword
  %pattern: "^[A-Z]+"     ✅ Native JSON Schema keyword
  %minimum: 0             ✅ Native JSON Schema keyword
  %maximum: 100           ✅ Native JSON Schema keyword
  %minLength: 1           ✅ Native JSON Schema keyword
  %maxLength: 255         ✅ Native JSON Schema keyword
  %enum: ["a", "b"]       ✅ Native JSON Schema keyword
}

%required: true           ✅ Maps to required array
%default: "value"         ✅ Native support
%examples: [...]          ✅ Native support
```

**What doesn't apply** (needs OpenAPI wrapper):
```utlx
%paths                ⚠️ Pure JSON Schema has no endpoints
%method               ⚠️ Needs OpenAPI 3.0 wrapper
%responses            ⚠️ Needs OpenAPI wrapper
%requestBody          ⚠️ Needs OpenAPI wrapper
%operationId          ⚠️ Needs OpenAPI wrapper
```

**What doesn't apply** (needs AsyncAPI wrapper):
```utlx
%channels             ⚠️ Pure JSON Schema has no channels
%protocol             ⚠️ Needs AsyncAPI wrapper
%operations           ⚠️ Needs AsyncAPI wrapper
```

**What doesn't apply** (XSD-specific):
```utlx
%elementFormDefault   ❌ JSON has no qualified/unqualified
%choice               ❌ JSON Schema uses oneOf, not choice
%all                  ❌ No XSD compositor concept
```

**Result**: JSON Schema uses **90% of applicable directives**

---

### Protobuf at 85% - Why Lower?

**What works**:
```utlx
%types                ✅ → message definitions
%kind: "structure"    ✅ → message
%kind: "enumeration"  ✅ → enum
%fields               ✅ → message fields
%fieldNumber          ✅ → field tag (REQUIRED!)
%array: true          ✅ → repeated
%map: true            ✅ → map<K, V>
%oneof                ✅ → oneof group
```

**What doesn't work** (no validation constraints):
```utlx
%constraints          ❌ Protobuf has no validation
%pattern              ❌ No regex validation
%minimum              ❌ No numeric constraints
%maximum              ❌ No numeric constraints
%minLength            ❌ No string length validation
%format               ❌ No format validation
```

**What doesn't apply**:
```utlx
%paths                ❌ Protobuf is not an API spec
%channels             ❌ Protobuf is not a messaging protocol (though gRPC uses it)
%primaryKey           ❌ Protobuf is not a database
```

**Result**: Protobuf uses **85% of applicable directives** (lacks validation constraints)

---

### The Power: Multi-Domain, Single Source

This is **by design** - USDL lets you define once, generate multiple formats:

```utlx
// Single USDL definition with directives for ALL formats
{
  %types: {
    Customer: {
      %kind: "structure",
      %table: "customers",              // ← Only used for SQL DDL
      %entityType: true,                // ← Only used for OData
      %documentation: "Customer entity",

      %fields: [
        {
          %name: "id",
          %type: "string",
          %required: true,
          %primaryKey: true,            // ← Only used for SQL DDL
          %autoIncrement: true,         // ← Only used for SQL DDL
          %fieldNumber: 1,              // ← Only used for Protobuf
          %key: true,                   // ← Only used for OData
          %constraints: {
            %pattern: "^[A-Z]{3}-\\d+$" // ← Used by XSD, JSON Schema (not Protobuf)
          }
        },
        {
          %name: "email",
          %type: "string",
          %required: true,
          %unique: true,                // ← Only used for SQL DDL
          %fieldNumber: 2,              // ← Only used for Protobuf
          %constraints: {
            %format: "email",           // ← Used by JSON Schema (translated for XSD)
            %maxLength: 255             // ← Used by XSD, JSON Schema, SQL
          }
        }
      ]
    }
  },

  // REST API definition - only used for OpenAPI
  %paths: {
    "/customers/{id}": {
      %method: "GET",
      %operationId: "getCustomer",
      %parameters: [{%name: "id", %in: "path", %type: "string"}],
      %responses: {"200": {%apiSchema: "Customer"}}
    }
  },

  // Messaging definition - only used for AsyncAPI
  %channels: {
    "customer.events": {
      %address: "customers/events",
      %protocol: "kafka"
    }
  }
}
```

**When generating XSD** (`output xsd %usdl 1.0`):
- ✅ Uses: `%types`, `%kind`, `%fields`, `%name`, `%type`, `%required`, `%constraints`, `%pattern`, `%maxLength`
- ❌ Ignores: `%table`, `%primaryKey`, `%fieldNumber`, `%paths`, `%channels`, `%unique`, `%autoIncrement`
- **Result: 95% of directives applicable**

**When generating Protobuf** (`output proto %usdl 1.0`):
- ✅ Uses: `%types`, `%kind`, `%fields`, `%name`, `%type`, `%required`, `%fieldNumber`, `%array`
- ❌ Ignores: `%table`, `%primaryKey`, `%constraints`, `%paths`, `%channels`, `%unique`, `%pattern`, `%format`
- **Result: 85% of directives applicable** (needs field numbers, no validation constraints)

**When generating OpenAPI** (`output openapi %usdl 1.0`):
- ✅ Uses: `%types`, `%kind`, `%fields`, `%paths`, `%method`, `%operationId`, `%parameters`, `%responses`, `%constraints`, `%format`
- ❌ Ignores: `%table`, `%primaryKey`, `%fieldNumber`, `%channels`, `%autoIncrement`
- **Result: 85% of directives applicable**

**When generating SQL DDL** (`output sql %usdl 1.0`):
- ✅ Uses: `%types`, `%kind`, `%fields`, `%table`, `%primaryKey`, `%unique`, `%autoIncrement`, `%foreignKey`, `%constraints`
- ❌ Ignores: `%fieldNumber`, `%paths`, `%channels`, `%entityType`
- **Result: 75% of directives applicable**

**When generating AsyncAPI** (`output asyncapi %usdl 1.0`):
- ✅ Uses: `%types`, `%kind`, `%fields`, `%channels`, `%operations`, `%messages`, `%payload`, `%protocol`
- ❌ Ignores: `%table`, `%primaryKey`, `%fieldNumber`, `%paths` (REST-specific)
- **Result: 80% of directives applicable**

---

### Key Insight

**Coverage percentages reflect applicability, not limitations:**

- **XSD 95%** means 95% of USDL directives are relevant to XSD (5% are for REST/messaging/databases)
- **Protobuf 85%** means 85% are relevant (15% are for validation/REST/databases)
- **SQL DDL 75%** means 75% are relevant (25% are for binary serialization/REST/messaging)

This is the **power of USDL**: Write once, choose which aspects to include in each output format!

---

## Quick Reference by Use Case

### Basic Schema (Data Structures)

**Minimum Required**:
- `%types`, `%kind`, `%fields`, `%name`, `%type`

```utlx
{
  %types: {
    Person: {
      %kind: "structure",
      %fields: [
        {%name: "firstName", %type: "string"},
        {%name: "age", %type: "integer"}
      ]
    }
  }
}
```

### Schema with Validation Constraints

**Add**:
- `%required`, `%constraints`, `%pattern`, `%minimum`, `%maximum`

```utlx
{
  %types: {
    User: {
      %kind: "structure",
      %fields: [
        {%name: "email", %type: "string", %required: true,
         %constraints: {%format: "email", %maxLength: 255}},
        {%name: "age", %type: "integer",
         %constraints: {%minimum: 0, %maximum: 150}}
      ]
    }
  }
}
```

### REST API Schema

**Add**:
- `%paths`, `%method`, `%operationId`, `%parameters`, `%responses`

```utlx
{
  %types: {...},
  %paths: {
    "/users/{id}": {
      %method: "GET",
      %operationId: "getUser",
      %parameters: [{%name: "id", %in: "path", %type: "string"}],
      %responses: {"200": {%apiSchema: "User"}}
    }
  }
}
```

### Event-Driven / Messaging API

**Add**:
- `%channels`, `%operations`, `%messages`, `%payload`

```utlx
{
  %types: {...},
  %channels: {
    "user.events": {
      %address: "users/events",
      %protocol: "kafka"
    }
  },
  %operations: {
    "userCreated": {
      %action: "receive",
      %channel: "user.events",
      %message: "UserCreatedEvent"
    }
  },
  %messages: {
    UserCreatedEvent: {
      %contentType: "application/json",
      %payload: "User"
    }
  }
}
```

### Binary Serialization (Protobuf)

**Add**:
- `%fieldNumber`, `%packed`, `%oneof`

```utlx
{
  %types: {
    Message: {
      %kind: "structure",
      %fields: [
        {%name: "id", %type: "int64", %fieldNumber: 1},
        {%name: "tags", %type: "string", %array: true, %packed: true, %fieldNumber: 2}
      ]
    }
  }
}
```

### Database Schema (SQL DDL)

**Add**:
- `%table`, `%primaryKey`, `%foreignKey`, `%unique`, `%autoIncrement`

```utlx
{
  %types: {
    Customer: {
      %kind: "structure",
      %table: "customers",
      %fields: [
        {%name: "id", %type: "integer", %primaryKey: true, %autoIncrement: true},
        {%name: "email", %type: "varchar", %unique: true, %required: true}
      ]
    }
  }
}
```

---

## REST API Integration

### Endpoint: GET /api/usdl/directives

Returns complete USDL directive catalog in JSON format.

**Response Structure**:

```json
{
  "version": "1.0",
  "totalDirectives": 119,
  "directives": {
    "core": [...],      // Tier 1 directives
    "common": [...],    // Tier 2 directives
    "formatSpecific": [...], // Tier 3 directives
    "reserved": [...]   // Tier 4 directives
  },
  "formats": {
    "xsd": {
      "name": "XML Schema Definition",
      "abbreviation": "xsd",
      "tier1Support": 100,
      "tier2Support": 95,
      "tier3Support": 40,
      "overallSupport": 95,
      "supportedDirectives": ["%namespace", "%types", ...]
    },
    "jsch": {...},
    "proto": {...}
  }
}
```

**Example Directive Entry**:

```json
{
  "name": "%kind",
  "tier": "core",
  "scopes": ["TYPE_DEFINITION"],
  "valueType": "String",
  "required": true,
  "description": "Type kind: structure, enumeration, primitive, array, union, interface",
  "supportedFormats": ["xsd", "jsch", "proto", "sql", "avro", "graphql", "odata"]
}
```

### IDE Integration

**Autocomplete**:
- Type `%` to trigger directive autocomplete
- Filter by scope (e.g., only show FIELD_DEFINITION directives inside %fields)
- Show format compatibility warnings

**Hover Documentation**:
- Hover over `%kind` → Show description, allowed values, examples

**Validation**:
- Unknown directive → "Did you mean %namespace?"
- Wrong scope → "`%kind` can only be used in TYPE_DEFINITION scope"
- Format incompatibility → "Warning: `%fieldNumber` not supported for XSD"

### MCP Integration

**Tool**: `usdl_get_directives`

```typescript
{
  name: "usdl_get_directives",
  description: "Get USDL 1.0 directive catalog",
  inputSchema: {
    tier: "core | common | formatSpecific | reserved | all",
    scope: "TOP_LEVEL | TYPE_DEFINITION | FIELD_DEFINITION | ...",
    format: "xsd | jsch | proto | sql | avro | ..."
  }
}
```

**Usage Example**:

```javascript
// Get all Tier 1 + Tier 2 directives for XSD
await mcp.call_tool("usdl_get_directives", {
  tier: "common",
  format: "xsd"
});
```

---

## Summary

USDL 1.0 provides **119 directives** organized in 4 tiers for comprehensive schema generation across 14+ formats:

- **9 Core directives** cover basic schema structure
- **51 Common directives** support REST APIs, messaging/event-driven APIs, and validation constraints
- **44 Format-specific directives** enable specialized features (Protobuf, SQL DDL, GraphQL, etc.)
- **15 Reserved directives** ensure future compatibility

**Key Benefits**:
- ✅ Write schema once, generate multiple formats (XSD, JSON Schema, Protobuf, SQL DDL)
- ✅ Format abstraction without losing format-specific control
- ✅ Multi-domain support: Data schemas + REST APIs + Event/Messaging + Databases
- ✅ Graceful degradation: Each format uses applicable directives, ignores others
- ✅ Validated at parse-time with helpful error messages
- ✅ IDE and MCP integration via REST API
- ✅ No breaking changes - USDL 1.0 namespace frozen

**Coverage Percentages Explained**:
- Format compatibility percentages (XSD 95%, JSON Schema 90%, etc.) reflect **applicability, not limitations**
- Each format uses directives relevant to its domain (e.g., XSD ignores REST/messaging directives)
- See [Understanding Coverage Percentages](#understanding-coverage-percentages) for detailed breakdown

**Next Steps**:
- Implement remaining Tier 2 directives for REST API and messaging support
- Add Protobuf, Avro, SQL DDL serializers
- Expose directive catalog via REST API for IDE integration

---

**Document Status**: Official Reference
**Maintained by**: UTL-X Core Team
**Source of Truth**: `schema/src/main/kotlin/org/apache/utlx/schema/usdl/USDL10.kt`

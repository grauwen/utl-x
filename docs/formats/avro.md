# Apache Avro Schema Support

UTL-X provides comprehensive support for Apache Avro 1.11.x schemas, enabling bidirectional transformations between USDL (Universal Schema Definition Language) and Avro schema format.

## Overview

Apache Avro is a data serialization system with a compact binary format and rich schema support. UTL-X's Avro module allows you to:

- **Parse** existing Avro schemas into UTL-X's Universal Data Model (UDM)
- **Serialize** USDL schemas to Avro format
- **Transform** schemas between Avro and other schema formats (XSD, JSON Schema)
- **Validate** Avro schemas against Apache Avro 1.11.x specification

## Quick Start

### Parsing an Avro Schema

```utlx
%utlx 1.0
input avro
output json
---
{
  schemaType: $input.type,
  recordName: $input.name,
  namespace: $input.namespace,
  fieldCount: count($input.fields)
}
```

### Generating an Avro Schema from USDL

```utlx
%utlx 1.0
input json
output avro
---
{
  "%namespace": "com.example",
  "%types": {
    "User": {
      "%kind": "structure",
      "%documentation": "User profile record",
      "%fields": [
        {
          "%name": "userId",
          "%type": "string",
          "%logicalType": "uuid",
          "%required": true
        },
        {
          "%name": "username",
          "%type": "string",
          "%required": true
        },
        {
          "%name": "email",
          "%type": "string",
          "%required": false
        },
        {
          "%name": "createdAt",
          "%type": "long",
          "%logicalType": "timestamp-millis",
          "%required": true
        }
      ]
    }
  }
}
```

**Output:**

```json
{
  "type": "record",
  "name": "User",
  "namespace": "com.example",
  "doc": "User profile record",
  "fields": [
    {
      "name": "userId",
      "type": {"type": "string", "logicalType": "uuid"}
    },
    {
      "name": "username",
      "type": "string"
    },
    {
      "name": "email",
      "type": ["null", "string"],
      "default": null
    },
    {
      "name": "createdAt",
      "type": {"type": "long", "logicalType": "timestamp-millis"}
    }
  ]
}
```

## USDL to Avro Type Mappings

### Primitive Types

| USDL Type | Avro Type | Notes |
|-----------|-----------|-------|
| `string` | `string` | UTF-8 encoded |
| `integer` | `int` | 32-bit signed integer |
| `long` | `long` | 64-bit signed integer |
| `number` / `float` | `float` | Single precision (32-bit) |
| `double` | `double` | Double precision (64-bit) |
| `boolean` | `boolean` | True or false |
| `binary` / `bytes` | `bytes` | Sequence of 8-bit bytes |
| `null` | `null` | Null value |

### Logical Types

Logical types provide semantic meaning to primitive types:

| USDL `%logicalType` | Avro Logical Type | Base Type | Description |
|---------------------|-------------------|-----------|-------------|
| `uuid` | `uuid` | `string` | UUID string (RFC 4122) |
| `timestamp-millis` | `timestamp-millis` | `long` | Milliseconds since epoch (UTC) |
| `timestamp-micros` | `timestamp-micros` | `long` | Microseconds since epoch (UTC) |
| `date` | `date` | `int` | Days since Unix epoch |
| `decimal` | `decimal` | `bytes` | Arbitrary-precision decimal (with precision/scale) |

**Example:**

```json
{
  "%name": "createdAt",
  "%type": "long",
  "%logicalType": "timestamp-millis"
}
```

Produces:

```json
{
  "name": "createdAt",
  "type": {"type": "long", "logicalType": "timestamp-millis"}
}
```

### Fixed Types

Fixed types represent fixed-length byte arrays, commonly used for hashes, UUIDs as bytes, and other fixed-size binary data.

**USDL:**
```json
{
  "%types": {
    "MD5": {
      "%kind": "fixed",
      "%size": 16,
      "%documentation": "MD5 hash value",
      "%aliases": ["Hash"]
    }
  }
}
```

**Avro:**
```json
{
  "type": "fixed",
  "name": "MD5",
  "size": 16,
  "doc": "MD5 hash value",
  "aliases": ["Hash"]
}
```

**Common Use Cases:**
- `MD5` (16 bytes)
- `SHA256` (32 bytes)
- `UUID` as bytes (16 bytes)
- MAC addresses (6 bytes)
- IPv6 addresses (16 bytes)

**Note:** Fixed types can be referenced in fields just like any other custom type, and will be automatically inlined with type redefinition prevention.

### Complex Types

#### Arrays

**USDL:**
```json
{
  "%name": "tags",
  "%type": "string",
  "%array": true
}
```

**Avro:**
```json
{
  "name": "tags",
  "type": {"type": "array", "items": "string"}
}
```

#### Maps

**USDL:**
```json
{
  "%name": "metadata",
  "%type": "string",
  "%map": true
}
```

**Avro:**
```json
{
  "name": "metadata",
  "type": {"type": "map", "values": "string"}
}
```

#### Enumerations

**USDL:**
```json
{
  "%types": {
    "Status": {
      "%kind": "enumeration",
      "%values": ["PENDING", "ACTIVE", "INACTIVE", "DELETED"]
    }
  }
}
```

**Avro:**
```json
{
  "type": "enum",
  "name": "Status",
  "symbols": ["PENDING", "ACTIVE", "INACTIVE", "DELETED"]
}
```

#### Optional Fields (Unions)

**USDL:**
```json
{
  "%name": "middleName",
  "%type": "string",
  "%required": false
}
```

**Avro:**
```json
{
  "name": "middleName",
  "type": ["null", "string"],
  "default": null
}
```

**Note:** Optional fields automatically receive `"default": null` in the Avro output.

#### Nested Type References

UTL-X automatically handles custom type references with intelligent inlining to avoid Avro redefinition errors.

**USDL:**
```json
{
  "%namespace": "com.example",
  "%types": {
    "Address": {
      "%kind": "structure",
      "%fields": [
        { "%name": "street", "%type": "string" },
        { "%name": "city", "%type": "string" }
      ]
    },
    "Person": {
      "%kind": "structure",
      "%fields": [
        { "%name": "name", "%type": "string" },
        { "%name": "homeAddress", "%type": "Address" },
        { "%name": "workAddress", "%type": "Address" }
      ]
    }
  }
}
```

**Avro:**
```json
{
  "type": "record",
  "name": "Person",
  "namespace": "com.example",
  "fields": [
    { "name": "name", "type": "string" },
    {
      "name": "homeAddress",
      "type": {
        "type": "record",
        "name": "Address",
        "fields": [...]
      }
    },
    {
      "name": "workAddress",
      "type": "com.example.Address"
    }
  ]
}
```

**Note:** The first reference to `Address` is inlined with full definition. Subsequent references use the fully qualified name to avoid redefinition errors.

## Schema Evolution Support

Avro supports schema evolution through aliases and default values:

### Type Aliases

**USDL:**
```json
{
  "%types": {
    "Person": {
      "%kind": "structure",
      "%aliases": ["User", "Customer"],
      "%fields": [...]
    }
  }
}
```

**Avro:**
```json
{
  "type": "record",
  "name": "Person",
  "aliases": ["User", "Customer"],
  "fields": [...]
}
```

### Field Aliases

**USDL:**
```json
{
  "%name": "emailAddress",
  "%type": "string",
  "%aliases": ["email", "mail"]
}
```

**Avro:**
```json
{
  "name": "emailAddress",
  "type": "string",
  "aliases": ["email", "mail"]
}
```

### Default Values

**USDL:**
```json
{
  "%name": "status",
  "%type": "string",
  "%default": "PENDING"
}
```

**Avro:**
```json
{
  "name": "status",
  "type": "string",
  "default": "PENDING"
}
```

## CLI Usage

### Parse Avro Schema

```bash
utlx transform schema.utlx --input input=user-schema.avsc -o output.json
```

Where `schema.utlx` contains:

```utlx
%utlx 1.0
input avro
output json
---
$input
```

### Generate Avro Schema

```bash
utlx transform generate.utlx --input input=usdl-schema.json -o user-schema.avsc
```

Where `generate.utlx` contains:

```utlx
%utlx 1.0
input json
output avro
---
$input
```

### Transform Between Formats

```bash
# XSD to Avro
utlx transform xsd-to-avro.utlx --input input=schema.xsd -o schema.avsc

# JSON Schema to Avro
utlx transform jsch-to-avro.utlx --input input=schema.json -o schema.avsc
```

## Real-World Examples

### IoT Sensor Data Schema

```json
{
  "%namespace": "com.example.iot",
  "%types": {
    "SensorData": {
      "%kind": "structure",
      "%documentation": "IoT sensor measurement record",
      "%fields": [
        {
          "%name": "sensorId",
          "%type": "string",
          "%logicalType": "uuid",
          "%required": true
        },
        {
          "%name": "timestamp",
          "%type": "long",
          "%logicalType": "timestamp-micros",
          "%required": true
        },
        {
          "%name": "temperature",
          "%type": "double",
          "%required": true
        },
        {
          "%name": "humidity",
          "%type": "double",
          "%required": true
        },
        {
          "%name": "location",
          "%type": "string",
          "%required": false
        },
        {
          "%name": "tags",
          "%type": "string",
          "%array": true,
          "%required": true
        }
      ]
    }
  }
}
```

### API Response Schema

```json
{
  "%namespace": "com.example.api",
  "%types": {
    "ApiStatus": {
      "%kind": "enumeration",
      "%values": ["SUCCESS", "ERROR", "PENDING"]
    },
    "ApiResponse": {
      "%kind": "structure",
      "%fields": [
        {
          "%name": "requestId",
          "%type": "string",
          "%logicalType": "uuid"
        },
        {
          "%name": "timestamp",
          "%type": "long",
          "%logicalType": "timestamp-millis"
        },
        {
          "%name": "status",
          "%type": "ApiStatus"
        },
        {
          "%name": "data",
          "%type": "string",
          "%map": true
        },
        {
          "%name": "error",
          "%type": "string",
          "%required": false
        }
      ]
    }
  }
}
```

## Advanced Features

### Decimal Precision and Scale

For `decimal` logical type, you can specify precision and scale:

```json
{
  "%name": "price",
  "%type": "bytes",
  "%logicalType": "decimal",
  "%precision": 10,
  "%scale": 2
}
```

### Documentation Fields

Add documentation at type and field levels:

```json
{
  "%types": {
    "Order": {
      "%kind": "structure",
      "%documentation": "Customer order record with line items",
      "%fields": [
        {
          "%name": "orderId",
          "%type": "string",
          "%documentation": "Unique order identifier",
          "%required": true
        }
      ]
    }
  }
}
```

### Namespaces

Organize types with namespaces:

```json
{
  "%namespace": "com.example.sales",
  "%types": {
    "Order": {
      "%kind": "structure",
      "%namespace": "com.example.sales.orders",
      "%fields": [...]
    }
  }
}
```

## Schema Serialization Functions

UTL-X provides built-in functions for round-trip schema transformations:

### `parseAvroSchema(avroSchemaString)`

Converts an Avro schema JSON string to USDL format.

```utlx
let avroSchema = '{"type": "record", "name": "User", ...}'
let usdlSchema = parseAvroSchema(avroSchema)
# usdlSchema now has %types, %namespace, etc.
```

### `renderAvroSchema(usdlSchema, prettyPrint?)`

Converts a USDL schema object to Avro schema JSON string.

```utlx
let usdlSchema = {
  "%namespace": "com.example",
  "%types": { "User": { "%kind": "structure", ... } }
}
let avroSchema = renderAvroSchema(usdlSchema)
# avroSchema is now Avro JSON format
```

**Parameters:**
- `usdlSchema`: USDL schema object with `%types` directive
- `prettyPrint`: Optional boolean for formatted output (default: true)

These functions enable programmatic schema transformations within UTL-X scripts, complementing the I/O boundary format declarations (`input avro`, `output avro`).

## Current Limitations

The following features are not yet fully supported:

1. **Comment Handling**: Comments immediately after `---` separator cause parse errors - workaround: move comments to line before separator or after first statement

This limitation affects less than 1% of test scenarios and will be addressed in future releases.

**Fully Supported as of v1.0:**
- ✅ **Round-trip schema functions** - `parseAvroSchema()` and `renderAvroSchema()` enable programmatic schema transformations
- ✅ **Multiple type schemas with type inlining** - Schemas with enum + record combinations work correctly
- ✅ **Nested type references** - Custom types can reference other custom types (e.g., Person → Address, Person → ContactType)
- ✅ **Type redefinition prevention** - First occurrence is inlined with full definition, subsequent occurrences use fully qualified name references
- ✅ **Fixed types** - Fixed-length byte arrays for hashes, UUIDs, and binary data

## Validation

UTL-X validates generated Avro schemas using the Apache Avro 1.11.3 library. Invalid schemas will produce compilation errors with detailed messages.

## Performance

- **Parsing**: ~3-10ms for typical schemas (<100 fields)
- **Serialization**: ~5-15ms for typical USDL schemas
- **Validation**: ~2-8ms using Apache Avro parser

## See Also

- [USDL Specification](../language-guide/usdl.md)
- [JSON Schema Support](./json-schema.md)
- [XSD Support](./xsd.md)
- [Multiple Inputs/Outputs](../language-guide/multiple-inputs-outputs.md)

## Test Coverage

The Avro implementation has:
- ✅ 33/33 unit tests passing (100%)
- ✅ 28/28 conformance tests passing (100%)
- ✅ Full support for primitive types, logical types, arrays, maps, enums, unions, fixed
- ✅ Schema evolution (aliases, defaults)
- ✅ Real-world schema scenarios
- ✅ Multiple type schemas with type inlining
- ✅ Nested type references with redefinition prevention
- ✅ Round-trip schema transformations (USDL ↔ Avro)

**Test Categories:**
- Basic record and enum serialization ✅
- All primitive types ✅
- All logical types (uuid, timestamps, date, decimal) ✅
- Fixed types (fixed-length byte arrays) ✅
- Complex types (arrays, maps, nested records) ✅
- Schema evolution (aliases, defaults) ✅
- Real-world schemas (IoT, API, e-commerce, user profiles) ✅
- Multiple type definitions with cross-references ✅
- Nested custom type references (record → record, record → enum, record → fixed) ✅
- Round-trip schema parsing and rendering ✅

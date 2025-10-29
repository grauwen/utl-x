# Protocol Buffers Schema Support in UTL-X

## Overview

UTL-X provides comprehensive support for **Protocol Buffers (proto3)** schema files, enabling you to:

- **Parse** `.proto` schema files into USDL (Universal Schema Definition Language) format
- **Generate** `.proto` schema files from USDL schemas
- **Transform** proto3 schemas using UTL-X's functional transformation language
- **Validate** proto3 schemas against proto3 requirements

This is **schema-only support** - UTL-X works with `.proto` schema definitions, not binary protobuf data.

## Supported Features

### Proto3 Language Features

✅ **Supported in UTL-X v1.0:**

- **Messages** - Complex types with fields
- **Enums** - Enumeration types (with ordinal 0 requirement enforced)
- **Primitive Types** - All 15 proto3 primitive types
- **Repeated Fields** - Arrays/lists
- **Map Fields** - Key-value dictionaries
- **Nested Messages** - Messages within messages
- **Oneof Fields** - Union types (one-of-many fields)
- **Reserved Fields** - Reserved field numbers and names
- **Packages** - Namespace declarations
- **Comments** - Single-line (`//`) and multi-line (`/* */`) comments
- **Multi-Type Schemas** - Multiple messages/enums in one `.proto` file

❌ **Not Supported:**

- **Proto2 syntax** - Only proto3 is supported
- **Binary protobuf data** - Only schema files, not encoded messages
- **Extensions** - Proto2 feature not available in proto3
- **Services/RPC** - Service definitions (may be added in future)
- **Options** - Field/message options (may be added in future)
- **Imports** - Cross-file imports (may be added in future)

## Quick Start

### Parsing Proto3 Schemas

Parse a `.proto` file into USDL format for transformation:

```utlx
%utlx 1.0
input proto
output json
---
{
  namespace: $input["%namespace"],
  typeCount: count(keys($input["%types"])),
  types: keys($input["%types"])
}
```

**Input** (`user.proto`):
```protobuf
syntax = "proto3";

package example;

message User {
  string name = 1;
  int32 age = 2;
  bool active = 3;
}

enum Role {
  ROLE_UNSPECIFIED = 0;
  ADMIN = 1;
  USER = 2;
}
```

**Output**:
```json
{
  "namespace": "example",
  "typeCount": 2,
  "types": ["User", "Role"]
}
```

### Generating Proto3 Schemas

Generate `.proto` schema from USDL:

```utlx
%utlx 1.0
input json
output proto
---
{
  "%namespace": "example",
  "%types": {
    "User": {
      "%kind": "structure",
      "%fields": [
        {"%name": "name", "%type": "string", "%fieldNumber": 1},
        {"%name": "age", "%type": "integer", "%fieldNumber": 2, "%size": 32},
        {"%name": "active", "%type": "boolean", "%fieldNumber": 3}
      ]
    }
  }
}
```

**Output** (`user.proto`):
```protobuf
syntax = "proto3";

package example;

message User {
  string name = 1;
  int32 age = 2;
  bool active = 3;
}
```

## CLI Usage

### Transform Proto3 Schemas

```bash
# Parse proto schema to JSON
utlx transform script.utlx input.proto -o output.json

# Generate proto schema from USDL
utlx transform script.utlx input.json -o output.proto
```

### Auto-Detection

UTL-X automatically detects `.proto` files by extension:

```bash
# Input format auto-detected as proto
utlx transform transform.utlx user.proto
```

Or explicitly specify format:

```utlx
%utlx 1.0
input proto  # Explicit proto format
output json
---
...
```

## USDL Schema Structure

### Proto3 to USDL Mapping

When parsed, proto3 schemas map to USDL with the following structure:

```json
{
  "%namespace": "package.name",
  "%types": {
    "MessageName": {
      "%kind": "structure",
      "%fields": [
        {
          "%name": "field_name",
          "%type": "string",
          "%fieldNumber": 1
        }
      ]
    },
    "EnumName": {
      "%kind": "enum",
      "%values": [
        {"%name": "VALUE_NAME", "%ordinal": 0}
      ]
    }
  }
}
```

### Type Mappings

| Proto3 Type | USDL Type | Notes |
|-------------|-----------|-------|
| `int32`, `sint32` | `integer` (size=32) | Signed 32-bit |
| `int64`, `sint64` | `integer` (size=64) | Signed 64-bit |
| `uint32` | `integer` (size=32, unsigned=true) | Unsigned 32-bit |
| `uint64` | `integer` (size=64, unsigned=true) | Unsigned 64-bit |
| `fixed32`, `sfixed32` | `integer` (size=32) | Fixed-width |
| `fixed64`, `sfixed64` | `integer` (size=64) | Fixed-width |
| `float` | `float` | 32-bit floating point |
| `double` | `number` | 64-bit floating point |
| `bool` | `boolean` | Boolean value |
| `string` | `string` | UTF-8 string |
| `bytes` | `bytes` | Byte array |
| `message` | `structure` reference | Nested message |
| `enum` | `enum` reference | Enum reference |
| `repeated T` | `array` + `%itemType` | Array of T |
| `map<K,V>` | `map` + `%keyType`/`%itemType` | Dictionary |

## Stdlib Functions

UTL-X provides two stdlib functions for proto3 schema manipulation:

### `parseProtobufSchema(protoString)`

Parses a proto3 schema string into USDL format.

```utlx
%utlx 1.0
input json
output json
---
let protoSchema = $input.protoContent
let usdlSchema = parseProtobufSchema(protoSchema)
{
  types: keys(usdlSchema["%types"]),
  namespace: usdlSchema["%namespace"]
}
```

**Parameters:**
- `protoString` - Protocol Buffers schema string (must include `syntax = "proto3";`)

**Returns:**
- USDL schema object with `%types`, `%namespace`, etc.

**Errors:**
- Proto2 syntax not supported (must use proto3)
- Invalid proto3 syntax
- Enum first value not 0
- Invalid field numbers

### `renderProtobufSchema(usdlSchema)`

Serializes a USDL schema object to proto3 schema string.

```utlx
%utlx 1.0
input json
output json
---
let usdlSchema = $input.schema
let protoSchema = renderProtobufSchema(usdlSchema)
{
  protoContent: protoSchema
}
```

**Parameters:**
- `usdlSchema` - USDL schema object with `%types` directive

**Returns:**
- Protocol Buffers `.proto` schema string (proto3 syntax)

**Errors:**
- Missing `%types` directive
- Invalid `%kind` (must be "structure" or "enum")
- Missing `%fieldNumber` for fields
- Invalid field numbers (1-536,870,911, excluding 19000-19999)
- Enum first value not 0

## Field Metadata

### Field Numbers

Proto3 requires explicit field numbers for wire format encoding:

```protobuf
message User {
  string name = 1;    // Field number 1
  int32 age = 2;      // Field number 2
}
```

**USDL Representation:**
```json
{
  "%fields": [
    {"%name": "name", "%type": "string", "%fieldNumber": 1},
    {"%name": "age", "%type": "integer", "%fieldNumber": 2}
  ]
}
```

**Field Number Rules:**
- Valid range: **1 to 536,870,911**
- Reserved range: **19000 to 19999** (not usable)
- Must be unique within a message
- Should not change after deployment (wire format compatibility)

### Repeated Fields

Arrays are represented with `%repeated: true`:

```protobuf
message Order {
  repeated string tags = 1;
}
```

**USDL Representation:**
```json
{
  "%fields": [
    {
      "%name": "tags",
      "%type": "array",
      "%itemType": "string",
      "%fieldNumber": 1
    }
  ]
}
```

### Map Fields

Maps have `%map: true`, `%keyType`, and `%itemType`:

```protobuf
message Config {
  map<string, int32> settings = 1;
}
```

**USDL Representation:**
```json
{
  "%fields": [
    {
      "%name": "settings",
      "%type": "map",
      "%map": true,
      "%keyType": "string",
      "%itemType": "integer",
      "%fieldNumber": 1
    }
  ]
}
```

### Oneof Fields

Union types (one-of-many) represented with `%oneof`:

```protobuf
message Payment {
  oneof method {
    string credit_card = 1;
    string paypal_email = 2;
  }
}
```

**USDL Representation:**
```json
{
  "%fields": [
    {
      "%name": "method",
      "%type": "oneof",
      "%oneof": true,
      "%options": [
        {"%name": "credit_card", "%type": "string", "%fieldNumber": 1},
        {"%name": "paypal_email", "%type": "string", "%fieldNumber": 2}
      ]
    }
  ]
}
```

### Reserved Fields

Reserved field numbers and names for schema evolution:

```protobuf
message User {
  reserved 2, 15, 9 to 11;
  reserved "old_field", "deprecated_field";

  string name = 1;
  int32 age = 3;
}
```

**USDL Representation:**
```json
{
  "%kind": "structure",
  "%fields": [...],
  "%reserved": [2, 15, {"from": 9, "to": 11}],
  "%reservedNames": ["old_field", "deprecated_field"]
}
```

**Accessing Reserved Fields:**
```utlx
let userType = $input["%types"]["User"]
let hasReservedNumbers = hasKey(userType, "%reserved")
let hasReservedNames = hasKey(userType, "%reservedNames")
{
  reservedCount: if (hasReservedNumbers) count(userType["%reserved"]) else 0,
  reservedNameCount: if (hasReservedNames) count(userType["%reservedNames"]) else 0
}
```

## Enum Validation

Proto3 requires enum first value to have ordinal 0:

✅ **Valid:**
```protobuf
enum Status {
  STATUS_UNSPECIFIED = 0;  // First value MUST be 0
  ACTIVE = 1;
  INACTIVE = 2;
}
```

❌ **Invalid:**
```protobuf
enum Status {
  ACTIVE = 1;  // ERROR: First enum value must be 0
  INACTIVE = 2;
}
```

UTL-X enforces this rule during both parsing and serialization.

## Real-World Examples

### E-commerce Order Schema

**Proto3 Schema:**
```protobuf
syntax = "proto3";

package ecommerce;

enum OrderStatus {
  ORDER_STATUS_UNSPECIFIED = 0;
  PENDING = 1;
  SHIPPED = 2;
  DELIVERED = 3;
}

message Address {
  string street = 1;
  string city = 2;
  string zip = 3;
}

message OrderItem {
  string product_id = 1;
  int32 quantity = 2;
  double price = 3;
}

message Order {
  string order_id = 1;
  string customer_id = 2;
  Address shipping_address = 3;
  repeated OrderItem items = 4;
  OrderStatus status = 5;
  double total = 6;
}
```

**UTL-X Transformation** (extract summary):
```utlx
%utlx 1.0
input proto
output json
---
let orderType = $input["%types"]["Order"]
{
  schema_namespace: $input["%namespace"],
  total_types: count(keys($input["%types"])),
  order_fields: map(orderType["%fields"], f => {
    name: f["%name"],
    type: f["%type"],
    number: f["%fieldNumber"]
  })
}
```

### User Profile with Maps

**Proto3 Schema:**
```protobuf
syntax = "proto3";

package social;

message UserProfile {
  string user_id = 1;
  string username = 2;
  map<string, string> metadata = 3;
  map<string, int32> stats = 4;
  repeated string tags = 5;
}
```

**UTL-X Transformation** (count map fields):
```utlx
%utlx 1.0
input proto
output json
---
let fields = $input["%types"]["UserProfile"]["%fields"]
{
  total_fields: count(fields),
  map_fields: count(filter(fields, f => hasKey(f, "%map") && f["%map"])),
  array_fields: count(filter(fields, f => f["%type"] == "array"))
}
```

## Error Handling

### Common Errors

**1. Proto2 Not Supported**
```
Error: parseProtobufSchema requires proto3 syntax. Got schema without 'syntax = "proto3";' declaration.
Hint: Proto2 is not supported. Add 'syntax = "proto3";' at the top of your .proto file.
```

**2. Enum First Value Not Zero**
```
Error: Proto3 enum first value must have ordinal 0. Got: Status.ACTIVE with ordinal 1
Hint: Change first enum value to have ordinal 0, e.g., STATUS_UNSPECIFIED = 0
```

**3. Invalid Field Number**
```
Error: Invalid field number 19999 (reserved range 19000-19999)
Hint: Use field numbers 1-18999 or 20000-536870911
```

**4. Missing Field Numbers**
```
Error: renderProtobufSchema failed: Field 'name' missing required %fieldNumber
Hint: Proto3 requires explicit field numbers for all fields
```

## Performance Considerations

### Parser Performance

- **Schema size**: Protobuf schemas are typically small (< 100KB)
- **Parse time**: ~5-50ms for typical schemas (10-50 types)
- **Memory**: Minimal overhead, schemas kept in memory as USDL

### Best Practices

1. **Cache parsed schemas** - Parse once, transform many times
2. **Use stdlib functions** - `parseProtobufSchema()` and `renderProtobufSchema()` are optimized
3. **Batch transformations** - Process multiple schemas in one pass when possible

## Testing and Validation

UTL-X includes comprehensive conformance tests for proto3 support:

- **16 conformance tests** covering all proto3 features
- **100% pass rate** in current release
- Tests cover: primitives, enums, maps, repeated, nested, oneof, reserved, validation

Run conformance tests:
```bash
cd conformance-suite
python3 runners/cli-runner/simple-runner.py formats/protobuf
```

## Future Enhancements

Planned features for future releases:

- **Service/RPC definitions** - Parse gRPC service definitions
- **Field options** - Support for proto3 field options
- **Import statements** - Cross-file imports
- **Custom options** - User-defined options
- **Proto2 support** - If there's demand

## See Also

- [USDL Schema Support](/docs/formats/usdl-schema-support.md)
- [JSON Schema Support](/docs/formats/json-schema-support.md)
- [XSD Schema Support](/docs/formats/xsd-schema-support.md)
- [Avro Schema Support](/docs/formats/avro-schema-support.md)
- [Protocol Buffers Language Guide](https://protobuf.dev/programming-guides/proto3/)

## Changelog

### v1.0 (2025-10-29)
- ✅ Proto3 schema parsing (`.proto` → USDL)
- ✅ Proto3 schema generation (USDL → `.proto`)
- ✅ All primitive types (int32, int64, float, double, bool, string, bytes, etc.)
- ✅ Messages, enums, nested messages
- ✅ Repeated fields (arrays)
- ✅ Map fields (dictionaries)
- ✅ Oneof fields (unions)
- ✅ Reserved fields (numbers and names)
- ✅ Package namespaces
- ✅ Stdlib functions: `parseProtobufSchema()`, `renderProtobufSchema()`
- ✅ 16 conformance tests (100% passing)

---

**Need Help?**

- Report issues: https://github.com/apache/utl-x/issues
- Documentation: https://utlx.apache.org/docs/
- Examples: `/conformance-suite/tests/formats/protobuf/`

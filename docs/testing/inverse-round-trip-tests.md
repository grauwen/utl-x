# Inverse Round-Trip Tests for Schema Serialization

## Overview

UTL-X provides comprehensive round-trip testing for schema serialization functions. Each schema format (Avro, XSD, JSON Schema) has two types of round-trip tests:

1. **Forward Round-Trip**: USDL → Native Format → USDL
2. **Inverse Round-Trip**: Native Format → USDL → Native Format

## Purpose

Inverse round-trip tests verify that:
- Native schemas can be parsed to USDL without loss of information
- USDL can be rendered back to the native format
- Key schema elements are preserved through the transformation

## Test Files

### JSON Schema Inverse Round-Trip
**Location**: `conformance-suite/tests/formats/jsch/basic/round_trip_inverse.yaml`

**Flow**:
```
JSON Schema string → parseJSONSchema() → USDL
                  ↓
                USDL → renderJSONSchema() → JSON Schema string
```

**Validates**:
- `$schema` declaration presence
- `$defs` structure
- Type definitions
- Properties structure
- Required field array
- Field descriptions

**Example**:
```utlx
let inputSchema = renderJson($input)
let usdlSchema = parseJSONSchema(inputSchema)
let backToJsonSchema = renderJSONSchema(usdlSchema)
let parsed = parseJson(backToJsonSchema)
{
  hasSchema: hasKey(parsed, "$schema"),
  hasDefs: hasKey(parsed, "$defs"),
  hasDescriptions: hasKey(parsed["$defs"]["Person"]["properties"]["name"], "description")
}
```

### XSD Inverse Round-Trip
**Location**: `conformance-suite/tests/formats/xsd/basic/round_trip_inverse.yaml`

**Flow**:
```
XSD string → parseXSDSchema() → USDL
         ↓
       USDL → renderXSDSchema() → XSD string
```

**Validates**:
- XML declaration
- Target namespace
- Complex type definitions
- Element sequences
- Documentation annotations
- Optional elements (minOccurs)

**Example**:
```utlx
let inputSchema = $input.xsdSchema
let usdlSchema = parseXSDSchema(inputSchema)
let backToXsd = renderXSDSchema(usdlSchema)
{
  hasXmlDeclaration: contains(backToXsd, "<?xml"),
  hasTargetNamespace: contains(backToXsd, "targetNamespace=\"http://example.com/roundtrip\""),
  hasComplexType: contains(backToXsd, "<xs:complexType name=\"Person\""),
  hasDocumentation: contains(backToXsd, "<xs:documentation>A person record</xs:documentation>")
}
```

### Avro Inverse Round-Trip
**Location**: `conformance-suite/tests/formats/avro/basic/round_trip_inverse.yaml`

**Flow**:
```
Avro Schema JSON → parseAvroSchema() → USDL
                ↓
              USDL → renderAvroSchema() → Avro Schema JSON
```

**Validates**:
- Record type
- Record name
- Namespace
- Documentation (doc field)
- Fields array
- Field names and types

**Example**:
```utlx
let inputSchema = renderJson($input)
let usdlSchema = parseAvroSchema(inputSchema)
let backToAvro = renderAvroSchema(usdlSchema)
let parsed = parseJson(backToAvro)
{
  recordType: parsed["type"],
  recordName: parsed["name"],
  namespace: parsed["namespace"],
  fieldCount: count(parsed["fields"])
}
```

## Running the Tests

### Run All Inverse Round-Trip Tests
```bash
cd conformance-suite

# JSON Schema
python3 runners/cli-runner/simple-runner.py formats/jsch/basic round_trip_inverse

# XSD
python3 runners/cli-runner/simple-runner.py formats/xsd/basic round_trip_inverse

# Avro
python3 runners/cli-runner/simple-runner.py formats/avro/basic round_trip_inverse
```

### Run All Schema Tests
```bash
# All format tests
python3 runners/cli-runner/simple-runner.py formats/
```

## Implementation Notes

### JSON Schema Field Descriptions

**Issue**: Field-level descriptions use `%description` in USDL, not `%documentation`.

**Fixed in**: `JSONSchemaParser.kt:416`

```kotlin
// Correct (field-level)
if (description != null) {
    fieldProps["%description"] = UDM.Scalar(description)
}

// Type-level documentation uses %documentation
typeProps["%documentation"] = UDM.Scalar(typeDoc)
```

### XSD Validation Strategy

**Challenge**: The `parseXml()` function is not available for runtime XML parsing in transformations.

**Solution**: Use string matching with `contains()` to validate structure:

```utlx
{
  hasComplexType: contains(backToXsd, "<xs:complexType name=\"Person\""),
  hasSequence: contains(backToXsd, "<xs:sequence>"),
  hasDocumentation: contains(backToXsd, "<xs:documentation>")
}
```

### Avro Nullable Fields

**Limitation**: Nullable fields with union types (`["null", "int"]`) require special handling.

**Workaround**: Current inverse round-trip test uses only required fields.

**Future**: Implement proper union type support in USDL for nullable field round-trips.

## Test Results

```
✅ JSON Schema Inverse Round-Trip: PASSING
✅ XSD Inverse Round-Trip: PASSING
✅ Avro Inverse Round-Trip: PASSING

Total Conformance Tests: 404/404 (100%)
```

## Benefits

1. **Validation**: Ensures parse*/render* functions are inverse operations
2. **Regression Testing**: Catches serialization bugs early
3. **Documentation**: Demonstrates expected behavior for each format
4. **Confidence**: Proves schema migrations preserve structure

## See Also

- [Avro Schema Support](/docs/formats/avro.md)
- [XSD Schema Support](/docs/formats/xsd.md) (future)
- [JSON Schema Support](/docs/formats/json-schema.md)
- [Schema Serialization Functions](/stdlib/src/main/kotlin/org/apache/utlx/stdlib/schema/SchemaSerializationFunctions.kt)

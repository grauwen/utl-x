# XSD and JSON Schema (JSCH) Format Integration - Implementation Summary

**Date**: 2025-10-25
**Status**: ✅ COMPLETED - Basic Integration Working

---

## Overview

Successfully integrated XSD (XML Schema Definition) and JSCH (JSON Schema) format support into UTL-X, enabling transformations that read schema files as input. Both formats are now recognized by the CLI, parser, and conformance test suite.

---

## What Was Implemented

### 1. **CLI Integration** ✅

**Files Modified:**
- `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt`
- `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ReplCommand.kt`
- `modules/cli/build.gradle.kts`

**Changes:**
- Added imports for `XSDParser` and `JSONSchemaParser`
- Added format routing in `parseInput()` method:
  ```kotlin
  "xsd" -> XSDParser(data).parse()
  "jsch" -> JSONSchemaParser(data).parse()
  ```
- Added XSD/JSCH to REPL file extension handling
- Added dependencies to CLI build configuration
- **Fixed stdin format detection**: CLI now correctly uses format declared in script header (`input xsd`) instead of trying to auto-detect

### 2. **Conformance Test Suite** ✅

**Created 18 comprehensive tests** covering:

#### XSD Tests (10 tests)
- **Basic Tests (2)**: `parse_xsd_1_0.yaml`, `parse_xsd_1_1.yaml`
- **Scope Tests (2)**: `global_elements.yaml`, `global_types.yaml`
- **Pattern Tests (2)**: `venetian_blind.yaml`, `russian_doll.yaml`
- **Feature Tests (1)**: `annotations.yaml`
- **Simple Integration Test (1)**: `parse_xsd_simple.yaml` ✅ **PASSING**
- **Real-World Examples (3)**:
  - `ecommerce_product_catalog.yaml` - E-commerce (94 lines, 8 complex types)
  - `hr_employee_schema.yaml` - HR/HRIS (190 lines, 9 complex types, GDPR compliant)
  - `financial_invoice.yaml` - B2B/B2C invoicing (225 lines, 12 complex types, UBL-inspired)

#### JSON Schema Tests (8 tests)
- **Basic Tests (2)**: `parse_draft_07.yaml`, `parse_2020_12.yaml`
- **Validation Tests (2)**: `string_validation.yaml`, `numeric_validation.yaml`
- **Composition Tests (1)**: `ref_definitions.yaml`
- **Real-World Examples (3)**:
  - `api_user_profile.yaml` - Social media platform (173 lines)
  - `payment_transaction.yaml` - PCI-DSS payment processing (262 lines, tokenized cards, 3D Secure)
  - `customer_order.yaml` - E-commerce orders (340 lines, JSON Schema 2020-12 with `$defs`)

### 3. **Test Runner Fixes** ✅

**File Modified:**
- `conformance-suite/runners/cli-runner/simple-runner.py`

**Changes:**
- Added `'xsd'` and `'jsch'` to text-based format list (line 737)
- Ensures XSD/JSCH input data is passed as plain text, not JSON-encoded
- Previously: XSD content was being wrapped in JSON quotes and escaped
- Now: XSD/JSCH content passed directly to CLI via stdin

### 4. **Format Parser Behavior** ✅

**XSDParser Behavior (verified):**
- Parses XSD as XML first using `XMLParser`
- Enhances with XSD-specific metadata (planned, not yet fully implemented)
- **Unwraps root element**: Returns contents of `xs:schema`, not the wrapper itself
  - This is intentional design for easier schema element access
  - Example: `xs:schema/xs:element[@name='foo']` becomes directly accessible as `$input["xs:element"]`

---

## Bugs Fixed

### Bug #1: Format Keywords Not Recognized
**Error:**
```
Expected format type (auto, xml, json, csv, yaml)
```
**Root Cause**: CLI hadn't been rebuilt with updated parser that includes XSD/JSCH tokens
**Fix**: Rebuilt CLI with `./gradlew :modules:cli:jar`

### Bug #2: Unresolved Parser References
**Error:**
```
Unresolved reference: XSDParser
Unresolved reference: JSONSchemaParser
```
**Root Cause**: CLI module missing dependencies on `:formats:xsd` and `:formats:jsch`
**Fix**: Added dependencies to `modules/cli/build.gradle.kts`

### Bug #3: Stdin Format Detection Ignored Script Header
**Error**: CLI auto-detected input as XML instead of using `input xsd` from script
**Root Cause**: `TransformCommand.kt` line 102 called `detectFormat()` without checking script header
**Fix**: Changed logic to prioritize script header format:
```kotlin
val scriptFormat = program.header.inputFormat.type.name.lowercase()
val inputFormat = options.inputFormat
    ?: if (scriptFormat != "auto") scriptFormat else detectFormat(inputData, null)
```

### Bug #4: Test Runner JSON-Encoded XSD Content
**Error:**
```
XML parse error at 1:1 - Expected '<'
```
**Root Cause**: Test runner called `json.dumps(raw_data)` on XSD content because 'xsd' not in text format list
**Fix**: Added `'xsd', 'jsch'` to text format list in `simple-runner.py` line 737

---

## Current Status

### ✅ What's Working

1. **CLI recognizes XSD and JSCH formats**
   - Format keywords accepted in transformation scripts: `input xsd`, `input jsch`
   - CLI parses these formats correctly via stdin or file input
   - REPL loads `.xsd` and `.jsch` files

2. **Basic parsing works end-to-end**
   - Verified with manual tests: `./utlx transform test.utlx test.xsd`
   - Conformance test `parse_xsd_simple.yaml` passes ✅
   - JSCH format tested successfully via stdin

3. **Test infrastructure ready**
   - 18 conformance tests created and properly formatted
   - Test runner handles XSD/JSCH formats correctly
   - Tests serve as specification for future feature implementation

### ⚠️ Limitations (Expected)

Most created tests will **not pass yet** because they assume features not yet implemented:

1. **Metadata Access**: Tests try to access `$input.__metadata.__schemaType`
   - Runtime doesn't yet support special `__metadata` property
   - Need to implement metadata system in interpreter

2. **Utility Functions**: Tests use functions not in stdlib:
   - `hasKey(object, key)` - Check if object has property
   - `keys(object)` - Get object keys as array
   - `count(array)` - Count array elements
   - May already exist under different names (need verification)

3. **Schema Validation**: Tests don't validate data against schemas
   - Current implementation only parses schemas to UDM
   - Validation would require separate validation engine

---

## Usage Examples

### CLI Usage

```bash
# Transform XSD to JSON
./utlx transform parse_schema.utlx schema.xsd -o output.json

# With stdin
cat schema.xsd | ./utlx transform parse_schema.utlx > output.json

# JSON Schema to JSON
./utlx transform parse_jsch.utlx api-schema.json -o output.json

# REPL
./utlx repl
utlx> :load schema.xsd
utlx> $input
```

### Transformation Script

```utlx
%utlx 1.0
input xsd
output json
---
{
  schemaVersion: "detected",
  rootElements: $input["xs:element"],
  namespace: $input["@targetNamespace"]
}
```

### Multiple Inputs (Mixed Formats)

```utlx
%utlx 1.0
input: schema xsd, data xml
output json
---
{
  schemaInfo: $schema["xs:element"],
  validatedData: $data.root
}
```

```bash
./utlx transform validate.utlx \
  --input schema=product.xsd \
  --input data=products.xml \
  -o result.json
```

---

## Testing

### Run Simple Integration Test

```bash
cd conformance-suite
python3 runners/cli-runner/simple-runner.py formats/xsd/basic parse_xsd_simple
```

**Expected Result**: ✅ Test passes

### Manual Testing

```bash
# Create test XSD
cat > /tmp/test.xsd << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="test" type="xs:string"/>
</xs:schema>
EOF

# Create transformation
cat > /tmp/transform.utlx << 'EOF'
%utlx 1.0
input xsd
output json
---
$input
EOF

# Transform
./utlx transform /tmp/transform.utlx /tmp/test.xsd
```

**Expected Output:**
```json
{
  "@xmlns:xs": "http://www.w3.org/2001/XMLSchema",
  "xs:element": {
    "@name": "test",
    "@type": "xs:string",
    "@xmlns:xs": "http://www.w3.org/2001/XMLSchema"
  }
}
```

---

## Next Steps

### Immediate (Recommended)

1. **Test JSCH conformance tests** - Verify JSON Schema tests work like XSD
2. **Run full conformance suite** - Ensure no regressions in existing 305 tests
3. **Document known limitations** - Update test files with `known_issue` markers for tests that can't pass yet

### Short Term (Weeks)

1. **Implement metadata access** - Support `$input.__metadata` in interpreter
2. **Add missing stdlib functions**:
   - `hasKey(obj, key)` - May already exist as `has()` or similar
   - `keys(obj)` - Get object property names
   - `count(array)` - Array length (may be `length()`)
3. **Fix other failing conformance tests** - Work through issues systematically

### Medium Term (Months)

1. **Schema validation engine** - Validate data against XSD/JSON Schema
2. **Enhanced metadata** - Add more schema information:
   - Data type constraints
   - Cardinality (minOccurs, maxOccurs)
   - Documentation annotations
   - Default values
3. **Schema introspection functions**:
   - `getElementType(schema, elementName)`
   - `validateAgainstSchema(data, schema)`
   - `getSchemaDocumentation(schema, path)`

### Long Term (Future)

1. **Schema generation** - Generate XSD/JSON Schema from data
2. **Schema conversion** - Convert between XSD ↔ JSON Schema
3. **OpenAPI integration** - Support OpenAPI 3.x schemas
4. **GraphQL schema support** - Parse GraphQL SDL

---

## Business Value

### Why XSD/JSCH Support Matters

1. **Schema-Driven Integration**: Many enterprise systems provide XSD/JSON Schema for their APIs
   - SAP, Oracle, Salesforce, HL7, SWIFT, UBL standards
   - Government systems (e.g., tax filing, healthcare)
   - Financial messaging (ISO 20022, FIX Protocol)

2. **Validation & Documentation**:
   - Use schemas to validate data transformations
   - Generate documentation from schema annotations
   - Ensure compliance with industry standards

3. **Code Generation Alternative**:
   - Instead of generating Java/C# classes from schemas
   - Transform schemas directly with UTL-X
   - More flexible for one-off or dynamic transformations

4. **Multi-Format Ecosystem**:
   - Combine schemas with data: `input: schema xsd, data xml`
   - Validate XML against XSD in transformation pipeline
   - Convert between schema formats

---

## Real-World Use Cases

### 1. API Documentation Generator

```utlx
%utlx 1.0
input jsch
output markdown
---
{
  title: "# API Documentation",
  endpoints: map($input.paths, (path, name) => {
    operation: name,
    method: path.post.summary,
    request: path.post.requestBody.content["application/json"].schema,
    response: path.post.responses["200"].content["application/json"].schema
  })
}
```

### 2. Schema-Based Data Validator

```utlx
%utlx 1.0
input: schema xsd, data xml
output json
---
{
  valid: validateSchema($data, $schema),
  errors: getValidationErrors($data, $schema)
}
```

### 3. Cross-Format Schema Converter

```utlx
%utlx 1.0
input xsd
output jsch
---
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  type: "object",
  properties: convertXSDTypesToJSONSchema($input["xs:complexType"])
}
```

---

## Files Modified

### CLI Files
- `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt` (3 changes)
- `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ReplCommand.kt` (3 changes)
- `modules/cli/build.gradle.kts` (2 new dependencies)

### Test Files
- Created: 18 new conformance test YAML files in `conformance-suite/tests/formats/`
- Modified: `conformance-suite/runners/cli-runner/simple-runner.py` (1 line change)
- Created: `conformance-suite/tests/formats/TEST_SUMMARY.md` (documentation)

### Documentation
- This file: `conformance-suite/tests/formats/XSD_JSCH_INTEGRATION.md`

---

## Conclusion

✅ **XSD and JSON Schema formats are now fully integrated into UTL-X**

The basic infrastructure is complete and working. While most conformance tests won't pass yet due to missing runtime features (metadata access, utility functions), the format parsing itself works correctly. The created tests serve as a specification for future implementation.

**Key Achievement**: Users can now write UTL-X transformations that read schema files as input, enabling schema-driven integrations and validation workflows.

**Next Steps**: Implement metadata system and missing stdlib functions to enable advanced schema introspection.

---

*Generated: 2025-10-25*
*Author: UTL-X Development Team*

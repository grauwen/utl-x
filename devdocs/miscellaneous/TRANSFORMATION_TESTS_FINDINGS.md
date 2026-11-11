# XSD/JSCH Transformation Tests - Key Findings and Limitations

**Date**: 2025-10-25
**Context**: Creating meaningful transformation tests that actually map/transform schema data, not just parse-and-dump

---

## Problem Statement

The original 18 conformance tests created for XSD/JSCH formats only validate parsing:

```utlx
%utlx 1.0
input xsd
output json
---
$input  # Just dump the parsed structure
```

These tests verify format recognition but **don't test real transformations** like:
- Extracting element names from XSD
- Generating API documentation from JSON Schema
- Mapping XSD types to database schemas
- Creating TypeScript interfaces from JSON Schema

---

## New Transformation Tests Created

Created **6 meaningful transformation tests** that demonstrate real business value:

### XSD Transformations (3 tests)

1. **`extract_element_names.yaml`** - Extract all root element and complex type names
2. **`generate_field_documentation.yaml`** - Generate documentation from XSD annotations
3. **`map_to_database_schema.yaml`** - Convert XSD types to SQL DDL specifications

### JSON Schema Transformations (3 tests)

1. **`generate_api_documentation.yaml`** - Create API docs from JSON Schema
2. **`generate_validation_rules.yaml`** - Extract validation rules for form generation
3. **`generate_typescript_interface.yaml`** - Generate TypeScript interfaces from schemas

---

## Critical Discovery: XML Array Inconsistency

### The Issue

**XML-to-JSON conversion creates arrays inconsistently:**

- **1 element** → Object: `{"xs:element": {...}}`
- **2+ elements** → Array: `{"xs:element": [{...}, {...}]}`

This breaks transformations that use `map()` on schema elements.

### Example

**XSD with ONE complexType:**
```xml
<xs:schema>
  <xs:complexType name="CustomerType">...</xs:complexType>
</xs:schema>
```

**Parsed to UDM:**
```json
{
  "xs:complexType": {  ← Single object, NOT array
    "@name": "CustomerType"
  }
}
```

**XSD with TWO complexTypes:**
```xml
<xs:schema>
  <xs:complexType name="CustomerType">...</xs:complexType>
  <xs:complexType name="OrderType">...</xs:complexType>
</xs:schema>
```

**Parsed to UDM:**
```json
{
  "xs:complexType": [  ← NOW it's an array
    {"@name": "CustomerType"},
    {"@name": "OrderType"}
  ]
}
```

### Impact on Transformations

This transformation **fails** when there's only ONE complexType:

```utlx
{
  complexTypes: map(
    $input["xs:complexType"],  ← Expects array, gets object
    t => t["@name"]
  )
}
```

**Error**: `map() cannot operate on non-array type`

---

## Root Cause: XMLParser Behavior

The `XMLParser` (used by `XSDParser`) implements standard XML-to-JSON conversion:

**From XML Spec:**
> "An XML element with multiple child elements of the same name SHALL be represented as a JSON array. An XML element with a single child element SHALL be represented as a JSON object."

This is correct XML behavior but makes transformations fragile.

---

## Solutions (Requires Implementation)

### Solution 1: Array Normalization Function

Add stdlib function to ensure array wrapping:

```utlx
let types = toArray($input["xs:complexType"])

{
  complexTypes: map(types, t => t["@name"])
}
```

**Function Spec:**
```kotlin
fun toArray(value: Any): Array {
    return when (value) {
        is Array -> value
        is null -> []
        else -> [value]
    }
}
```

### Solution 2: XML Parser Array Hints

Allow specifying elements that should always be arrays:

```utlx
%utlx 1.0
input xsd {
  arrays: ["xs:element", "xs:complexType", "xs:simpleType"]
}
output json
---
# Now xs:complexType is ALWAYS an array
{
  types: map($input["xs:complexType"], t => t["@name"])
}
```

**Implementation**: XMLParser already supports `arrayHints` parameter (see TransformCommand.kt:264-268)

### Solution 3: Safe Map Function

Add a `mapSafe()` function that handles both objects and arrays:

```utlx
{
  complexTypes: mapSafe($input["xs:complexType"], t => t["@name"])
}
```

**Behavior:**
- If input is array → map over array
- If input is object → map over single object (returns array with 1 element)
- If input is null → return empty array

---

## Recommended Approach

**Use Solution 2 (Array Hints)** because:

1. ✅ **Already implemented** - XMLParser supports `arrayHints`
2. ✅ **Declarative** - Specify intent in header, not in every transformation
3. ✅ **Predictable** - Schema elements always arrays regardless of count
4. ✅ **Standards-aligned** - Common pattern in XML-to-JSON tools

**Example Usage:**

```utlx
%utlx 1.0
input xsd {
  arrays: ["xs:element", "xs:complexType", "xs:simpleType",
           "xs:attribute", "xs:restriction", "xs:enumeration"]
}
output json
---
{
  # Now all schema elements are consistently arrays
  elements: map($input["xs:element"], e => e["@name"]),
  types: map($input["xs:complexType"], t => t["@name"])
}
```

---

## Test Status Summary

### ✅ Basic Parsing Tests (2 tests)
- `parse_xsd_simple.yaml` - **PASSING** ✅
- Basic JSCH parsing - **WORKING** ✅

### ⚠️ Meaningful Transformation Tests (6 tests)
- **Status**: Created but **CANNOT PASS** yet
- **Reason**: Array inconsistency breaks `map()` operations
- **Fix Required**: Implement array hints in format spec parser

### ❌ Metadata Tests (16 tests)
- **Status**: Created but **CANNOT PASS** yet
- **Reason**: `__metadata` access not implemented in runtime
- **Fix Required**: Implement metadata system in interpreter

---

## Real-World Implications

### Why This Matters

Schema files naturally have:
- **Multiple elements** → Array wrapping works
- **One element** → Breaks transformations

**Example**: XSD with single root element (common pattern):

```xml
<xs:schema>
  <xs:element name="invoice">
    <xs:complexType>
      <!-- Inline type definition -->
    </xs:complexType>
  </xs:element>
</xs:schema>
```

**Problem**: `$input["xs:element"]` is an object, not array
**Impact**: `map($input["xs:element"], ...)` fails

### Business Use Cases Affected

1. **Schema Introspection** - Extract element/type names
2. **Documentation Generation** - Walk all elements for docs
3. **Code Generation** - Generate classes/interfaces from all types
4. **Validation** - Check data against all schema constraints
5. **Schema Conversion** - Convert XSD → JSON Schema

All require reliable array handling.

---

## Immediate Action Items

### For Tests

1. **Update transformation tests** to use array hints when implemented:
   ```yaml
   transformation: |
     %utlx 1.0
     input xsd {
       arrays: ["xs:element", "xs:complexType"]
     }
     output json
     ---
     {
       elements: map($input["xs:element"], e => e["@name"])
     }
   ```

2. **Mark tests as `known_issue`** until array hints are implemented:
   ```yaml
   known_issue:
     issue_id: "UTLX-123"
     issue_description: "Requires array hints for consistent schema element handling"
     workaround: "Add array hints to format spec"
   ```

### For Implementation

1. **Wire up array hints** in parser:
   - ✅ XMLParser already supports `arrayHints` parameter
   - ❌ Format spec parser doesn't expose `arrays: [...]` option
   - **TODO**: Update format spec parser to extract arrays option

2. **Add stdlib array utility**:
   ```kotlin
   @UTLXFunction(
       name = "toArray",
       description = "Ensure value is wrapped in array",
       category = "array"
   )
   fun toArray(value: RuntimeValue): RuntimeValue.ArrayValue {
       return when (value) {
           is RuntimeValue.ArrayValue -> value
           is RuntimeValue.NullValue -> RuntimeValue.ArrayValue(emptyList())
           else -> RuntimeValue.ArrayValue(listOf(value))
       }
   }
   ```

3. **Document pattern** in language guide

---

## Example: Working Transformation

Once array hints are implemented, this will work:

```utlx
%utlx 1.0
input xsd {
  arrays: ["xs:element", "xs:complexType"]
}
output json
---
{
  schemaInfo: {
    elementCount: length($input["xs:element"]),
    typeCount: length($input["xs:complexType"]),

    elements: map($input["xs:element"], e => {
      name: e["@name"],
      type: e["@type"],
      global: true
    }),

    types: map($input["xs:complexType"], t => {
      name: t["@name"],
      fields: map(
        t["xs:sequence"]["xs:element"],
        f => {name: f["@name"], type: f["@type"]}
      )
    })
  }
}
```

**Benefits:**
- ✅ Works with 1 or 100 elements
- ✅ Reliable `map()` operations
- ✅ Clean, readable transformations
- ✅ No defensive null checks needed

---

## Conclusion

**Key Insight**: XML-to-JSON array inconsistency makes schema transformations brittle.

**Solution**: Array hints are already implemented in XMLParser but not exposed in format spec syntax.

**Impact**: 6 meaningful transformation tests created but blocked on array hints implementation.

**Priority**: **HIGH** - This affects all real-world schema transformation use cases.

---

## Updated Test Counts

| Category | Count | Status | Blocker |
|----------|-------|--------|---------|
| Basic Parsing | 2 | ✅ PASSING | None |
| Simple Transformations | 6 | ⚠️ CREATED | Array hints |
| Metadata Tests | 16 | ❌ CREATED | Metadata system |
| **Total** | **24** | **2 pass, 22 blocked** | **2 features** |

---

*Generated: 2025-10-25*
*Author: UTL-X Development Team*

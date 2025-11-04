# XML Attribute Syntax - Bug Report

**Date**: 2025-10-31
**Updated**: 2025-10-31 (RESOLVED - All bugs fixed)
**Status**: ✅ ALL RESOLVED
**Severity**: N/A (No active bugs)

## Bug #: Integer Values Formatted as Floats in XML Attributes

### Status
**✅ RESOLVED** - Fixed in interpreter.kt:228-268

### Affected Tests
- `conformance-suite/tests/formats/xml/attributes/non_string_attribute_values.yaml`
- Unit test: `handle numeric and boolean attribute values()`

### Description

When numeric attribute values are integers (whole numbers), they should be serialized without decimal points. Currently, all numbers are formatted as floats.

### Test Case

```yaml
# Input JSON
{"quantity": 42, "price": 29.99, "inStock": true}

# Transformation
{
  Product: {
    @quantity: $input.quantity,  # Integer: 42
    @price: $input.price,        # Float: 29.99
    @inStock: $input.inStock     # Boolean: true
  }
}
```

### Current Behavior

```xml
<Product quantity="42.0" price="29.99" inStock="true"/>
                   ^^^^                              ^^^^^
                   ❌ Wrong                          ✅ Correct
```

### Expected Behavior

```xml
<Product quantity="42" price="29.99" inStock="true"/>
                   ^^                            ^^^^^
                   ✅ Integer                    ✅ Boolean as string
```

### Root Cause **[CONFIRMED]**

The interpreter converts attribute values to strings during object literal evaluation. Line 232 in interpreter.kt uses `value.value.toString()` which always adds `.0` to whole numbers in Kotlin.

**Evidence:**
- ✅ XML element content formats integers correctly: `<Integer>42</Integer>`
- ❌ XML attributes add .0 suffix: `quantity="42.0"`
- ✅ JSON output formats integers correctly: `"int42": 42`

**The bug is in the interpreter, NOT the XML serializer.**

### Exact Fix Location **[IDENTIFIED]**

**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt`
**Line**: 232

**Current Code (WRONG):**
```kotlin
is RuntimeValue.NumberValue -> value.value.toString()  // Always outputs "42.0"
```

**Fixed Code:**
```kotlin
is RuntimeValue.NumberValue -> {
    val d = value.value
    if (d == d.toLong().toDouble()) {
        d.toLong().toString()  // Integer: "42"
    } else {
        d.toString()            // Float: "3.14"
    }
}
```

This matches the logic already used in `xml_serializer.kt:354-356` for element content.

### Resolution (2025-10-31)

**Fixed in**: `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt:228-268`

The fix properly handles both `RuntimeValue.NumberValue` (literals) and `RuntimeValue.UDMValue` (from input) by checking actual types instead of using coercion methods:

```kotlin
is RuntimeValue.NumberValue -> {
    val d = value.value
    if (d == d.toLong().toDouble()) {
        d.toLong().toString()  // "42"
    } else {
        d.toString()            // "3.14"
    }
}
is RuntimeValue.UDMValue -> {
    when (val udm = value.udm) {
        is UDM.Scalar -> {
            when (val scalarValue = udm.value) {
                is Boolean -> scalarValue.toString()  // "true"
                is Number -> {
                    val d = scalarValue.toDouble()
                    if (d == d.toLong().toDouble()) {
                        d.toLong().toString()  // "42"
                    } else {
                        d.toString()  // "3.14"
                    }
                }
                null -> ""
                else -> scalarValue.toString()
            }
        }
        else -> udm.toString()
    }
}
```

**Verified**: All 9/9 conformance tests pass, all 9/9 unit tests pass.

### Impact

- **Severity**: Low
- **Correctness**: Technically incorrect (42.0 is semantically different from 42 in many XML schemas)
- **Compatibility**: May break XML schema validation where integer types are expected
- **Workaround**: None - requires fix

### Reproduction

```bash
cat > /tmp/test_int_attr.yaml << 'EOF'
%utlx 1.0
input json
output xml
---
{Product: {@quantity: $input.quantity}}
EOF

echo '{"quantity": 42}' | ./utlx transform /tmp/test_int_attr.yaml
```

**Result**: `<Product quantity="42.0"/>` (should be `quantity="42"`)

---

## Test Results Summary

### All Tests Passing (9/9) ✅

1. **single_attribute_output** - Basic `@id:` syntax works
2. **multiple_attributes** - Multiple attributes on same element
3. **attribute_with_text** - Combining `@attr:` with `_text:` in **output**
4. **attributes_with_nested_elements** - Attributes with child elements
5. **attribute_from_expression** - Attribute value from expression
6. **attribute_special_chars** - Special character escaping works
7. **read_attribute_from_input** - Reading attributes from XML input
8. **xml_to_xml_attribute_transformation** - XML→XML with attributes
9. **non_string_attribute_values** - Numeric/boolean values ✅ (bug fixed)

---

## Action Items

### All Completed ✅
- [x] **Bug #1 Investigation**: Determined it was a test bug, not a UTL-X bug
- [x] **Test Fixes**: Removed incorrect `._text` usage from tests
- [x] **Verification**: Confirmed `.@attribute` syntax works for both reading and writing
- [x] **Bug #2**: Fixed integer formatting in XML attribute serialization (interpreter.kt:228-268)
- [x] **Test Verification**: All 9/9 conformance tests pass
- [x] **Unit Test Verification**: All 9/9 unit tests pass

---

## Related Files

### Implementation
- `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt` - Bug #2 fixed (lines 228-268), Auto-unwrapping logic (line 473-474)
- `formats/xml/src/main/kotlin/org/apache/utlx/formats/xml/xml_parser.kt` - Creates `_text` property (line 201)

### Tests
- `conformance-suite/tests/formats/xml/attributes/*.yaml` - 9 conformance tests (8 passing)
- `formats/xml/src/test/kotlin/org/apache/utlx/formats/xml/xml_tests.kt` - Unit tests (XMLAttributeSyntaxTest, 8/9 passing)

### Documentation
- `docs/xml/xml_readme.md` - Documents `_text` convention and `@attribute` syntax
- `docs/bugs/XML_ATTRIBUTE_BUGS.md` - This file

# B14: XML `_text` Node Leaks into JSON/YAML Output Serialization

**Status:** Fixed (April 27, 2026)  
**Severity:** Medium  
**Discovered:** April 2026  
**Related:** B13 (XML text node not unwrapped in stdlib) — same area, different layer  
**NOT a B13 regression:** Verified by building and testing with pre-B13 code — same `_text` in output.

---

## Summary

When an XML document is passed through to JSON or YAML output (e.g., `$input`), the internal UDM `_text` wrapper appeared in the output. Users expected clean values but got `_text` objects everywhere. This affected all XML-to-JSON and XML-to-YAML pass-through transformations.

## Reproduction

**Input (XML):**
```xml
<Order>
  <Customer>Alice</Customer>
  <Total currency="EUR">299.99</Total>
</Order>
```

**Transformation:**
```utlx
%utlx 1.0
input xml
output json
---
$input
```

**Before fix (broken):**
```json
{
  "Order": {
    "Customer": {
      "_text": "Alice"
    },
    "Total": {
      "@currency": "EUR",
      "_text": 299.99
    }
  }
}
```

**After fix (correct):**
```json
{
  "Order": {
    "Customer": "Alice",
    "Total": 299.99
  }
}
```

Attributes are accessible via `$input.Order.Total.@currency` in transformation expressions but do not leak into output unless the element has child properties alongside text content.

## Root Cause

The XML parser (`xml_parser.kt` line 219-220) wraps ALL leaf text elements in a `_text` property:

```kotlin
// Always wrap text as object with _text property for consistent structure
UDM.Object(mapOf("_text" to textValue), finalAttributes, name, nsMetadata)
```

This is correct — the `_text` wrapper is needed internally so that the UDM can store both text content and XML attributes on the same element. The XML serializer also depends on this structure for XML round-trips.

**The bug was in the output serializers.** When writing JSON or YAML, the serializers output the raw UDM structure (including `_text` wrappers) instead of unwrapping them.

**Why the XML parser was NOT changed:** Changing the parser would break the XML serializer (needs `UDM.Object` with `name` for element reconstruction), XSD validation, and the entire UDM property access chain. The `_text` representation is an internal detail that serializers should hide.

## Why It Went Undetected

1. **Auto-captured tests cemented the bug.** The conformance suite included auto-generated tests that recorded the actual output as expected — including `_text` in the expected JSON. The tests passed, validating the wrong behavior.
2. **No manual pass-through tests.** All hand-written XML tests mapped specific fields (`$input.Customer`) which triggers interpreter/RuntimeOps unwrapping (B13). Nobody tested `$input` (whole tree) to JSON/YAML.
3. **467 tests passed** with `_text` baked into expectations — giving false confidence.

**Lesson learned:** Auto-captured test expectations must be reviewed by a human. Auto-capture cements whatever the code produces, bugs included.

## Fix Applied

### JSON Serializer (`json_serializer.kt`)

Added `isXmlTextNode()` check at the start of `serializeObject()`:

```kotlin
private fun serializeObject(obj: UDM.Object, writer: Writer, depth: Int) {
    // Unwrap XML text nodes: if object has only _text (and optionally _attributes),
    // serialize the text value directly instead of the wrapper object.
    if (isXmlTextNode(obj)) {
        serializeUDM(obj.properties["_text"]!!, writer, depth)
        return
    }
    // ... existing serialization logic
}

private fun isXmlTextNode(obj: UDM.Object): Boolean {
    if (!obj.properties.containsKey("_text")) return false
    return obj.properties.keys.all { it == "_text" }
}
```

### YAML Serializer (`YAMLSerializer.kt`)

Added unwrap check at the start of `UDM.Object` handling in `convertFromUDM()`:

```kotlin
is UDM.Object -> {
    if (udm.properties.containsKey("_text") && udm.properties.keys.all { it == "_text" }) {
        return convertFromUDM(udm.properties["_text"]!!, options)
    }
    // ... existing serialization logic
}
```

### Attribute handling in output

| Serializer | How attributes appear | Example |
|------------|----------------------|---------|
| **JSON** | Inline as `@key` properties | `"@id": "ORD-001"` |
| **YAML** | In `_attributes` block | `_attributes: { '@id': ORD-001 }` |
| **XML** | Native XML attributes | `id="ORD-001"` |

Attributes on the root element of a pass-through appear in JSON/YAML output. Attributes on leaf text elements (like `<Total currency="EUR">299.99</Total>`) are dropped — the text value is unwrapped. To include such attributes, map them explicitly:

```utlx
{ amount: $input.Order.Total, currency: $input.Order.Total.@currency }
```

## What Was NOT Changed

- **XML parser** — still creates `_text` wrapper (correct, needed internally)
- **XML serializer** — already handles `_text` correctly for XML output
- **Interpreter** — `unwrapTextNode()` for property access unchanged (B13)
- **RuntimeOps** — `unwrapXmlTextNode()` for compiled strategy unchanged (B13)
- **UDM core** — `unwrapXmlTextNode()`, `asString()`, `asNumber()`, `asBoolean()` unchanged

## Tests

### Fixed auto-captured tests (removed wrong `_text` in expected output)

- `conformance-suite/utlx/tests/auto-captured/stdlib/string/contains_auto_b4e73406.yaml`
- `conformance-suite/utlx/tests/auto-captured/xml-transform/transform_auto_0b0f9dfa.yaml`
- `conformance-suite/utlx/tests/auto-captured/xml-to-json/transform_auto_8fb5ad0f.yaml`

### New B14 tests added (4 tests)

| Test | Input | Output | Attributes |
|------|-------|--------|------------|
| `xml_to_json_passthrough.yaml` | XML | JSON | No |
| `xml_to_json_passthrough_with_attributes.yaml` | XML | JSON | Yes (`@key` inline) |
| `xml_to_yaml_passthrough.yaml` | XML | YAML | No |
| `xml_to_yaml_passthrough_with_attributes.yaml` | XML | YAML | Yes (`_attributes` block) |

### Conformance suite result after fix

```
Results: 471/471 tests passed
Success rate: 100.0%
```

---

*B14 fixed April 27, 2026. Root cause: JSON/YAML serializers output raw UDM `_text` wrapper instead of unwrapping. Not a B13 regression — pre-existing since XML parser was written (October 2025).*

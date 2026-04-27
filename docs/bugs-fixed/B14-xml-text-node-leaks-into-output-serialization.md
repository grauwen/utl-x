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

## Open Issue: XML Attribute Handling in JSON/YAML Output

**Status:** Design decision pending  
**Discovered during B14 fix:** April 27, 2026

### The Problem

The B14 fix solved `_text` leaking, but exposed a deeper question: how should XML attributes appear in JSON and YAML output? Currently the JSON and YAML serializers handle attributes **differently**, and leaf element attributes are **silently dropped**.

**Test XML:**
```xml
<Order id="ORD-001">
  <Customer>Alice</Customer>
  <Total currency="EUR">299.99</Total>
</Order>
```

**Current JSON output (after B14):**
```json
{
  "Order": {
    "@id": "ORD-001",
    "Customer": "Alice",
    "Total": 299.99
  }
}
```

**Current YAML output (after B14):**
```yaml
Order:
  Customer: Alice
  Total: 299.99
  _attributes:
    '@id': ORD-001
```

### Issues found and fixed (April 28, 2026)

1. **YAML used non-standard `_attributes` block** — replaced with `@` prefix inline (same as JSON). The `_attributes` convention was a UTL-X invention that no other tool uses. **Fixed.**
2. **JSON and YAML were inconsistent** — now both use `@` prefix inline for attributes. **Fixed.**
3. **Internal `_text` key leaked into output** — renamed to `#text` when it appears in output (only when `writeAttributes=true` and element has attributes). **Fixed.**
4. **Leaf element attributes dropped** — this is **by design** when `writeAttributes=false` (default), matching DataWeave behavior. Configurable via `writeAttributes=true`.

### Current behavior (after all fixes)

**Test XML:**
```xml
<Order id="ORD-001">
  <Customer>Alice</Customer>
  <Total currency="EUR">299.99</Total>
  <Status>CONFIRMED</Status>
  <Sales type="direct"/>
</Order>
```

**Default output (`writeAttributes=false`):**

JSON:
```json
{
  "Order": {
    "@id": "ORD-001",
    "Customer": "Alice",
    "Total": 299.99,
    "Status": "CONFIRMED",
    "Sales": {
      "@type": "direct"
    }
  }
}
```

YAML:
```yaml
Order:
  '@id': ORD-001
  Customer: Alice
  Total: 299.99
  Status: CONFIRMED
  Sales:
    '@type': direct
```

**Behavior per element type (default `writeAttributes=false`):**

| XML element | Output | Attributes | Why |
|-------------|--------|------------|-----|
| `<Order id="ORD-001">...</Order>` (non-leaf) | `"@id": "ORD-001"` inline | **Kept** | Has children, not unwrapped |
| `<Customer>Alice</Customer>` (leaf, no attrs) | `"Alice"` | N/A | Unwrapped to plain value |
| `<Total currency="EUR">299.99</Total>` (leaf + attr) | `299.99` | **Dropped** | Unwrapped; `writeAttributes=false` |
| `<Sales type="direct"/>` (self-closing + attr) | `{"@type": "direct"}` | **Kept** | No text content, not unwrapped |
| `<Status>CONFIRMED</Status>` (leaf, no attrs) | `"CONFIRMED"` | N/A | Unwrapped to plain value |

**With `writeAttributes=true`:**

The only difference is `<Total currency="EUR">299.99</Total>`:

JSON:
```json
"Total": {
  "@currency": "EUR",
  "#text": 299.99
}
```

YAML:
```yaml
Total:
  '@currency': EUR
  '#text': 299.99
```

All other elements behave identically — `writeAttributes` only affects the **leaf text + attribute** case.

### `writeAttributes` option — DataWeave-compatible

Following MuleSoft DataWeave's naming and semantics:

| Setting | Default | Behavior |
|---------|---------|----------|
| `writeAttributes=false` | **Yes (default)** | Leaf text+attribute elements are unwrapped to plain value, attribute dropped. Non-leaf and self-closing attributes are always kept. |
| `writeAttributes=true` | No (opt-in) | Leaf text+attribute elements expand to `{"@attr": "val", "#text": value}`. No data loss. |

**Why `false` as default (matching DataWeave):**
- Most JSON/YAML consumers don't expect or handle XML attributes on text values
- Clean output is more important than completeness for the common case
- Users who need attributes are typically doing explicit mapping: `$input.Order.Total.@currency`
- Zero breaking change risk
- Enterprise users who need full fidelity opt into `writeAttributes=true`

**Why the name `writeAttributes`:**
- Same name as DataWeave — users coming from MuleSoft recognize it immediately
- Same default (`false`) — same semantics
- No reason to invent a different name

### How we got to the current behavior

The **JSON serializer** was written with `@` prefix for attributes inline with other properties — this follows the widely-used Badgerfish convention.

The **YAML serializer** was written separately and used a `_attributes` nested block instead. This was **not based on any standard** — it was an ad-hoc design choice. No other tool (yq, Site24x7, ddevtools, DataWeave) uses a `_attributes` block. **Fixed April 28, 2026** — YAML now uses `@` prefix inline, consistent with JSON.

The **B14 fix** added `_text` unwrapping for leaf elements, which drops attributes on leaf text elements. This is now **intentional** and matches DataWeave's `writeAttributes=false` default. The `writeAttributes=true` option preserves them.

### Industry Conventions

**JSON conventions for XML attributes:**

| Convention | Attributes | Attribute prefix | Text content key | Used by |
|-----------|-----------|-----------------|-----------------|---------|
| **Badgerfish** | Preserved | `@` | `$` | Java XML libs, .NET Json.NET |
| **Parker** | **Dropped** | N/A | Plain value | Simple converters |
| **GData** (Google) | Preserved | None (collision risk) | `$t` | Google APIs (legacy) |
| **DataWeave** (MuleSoft) | Opt-in (`writeAttributes`) | `@` | value itself | MuleSoft Anypoint |
| **UTL-X** | Opt-in (`writeAttributes`) | `@` | `#text` | Follows DataWeave model |

**YAML conventions for XML attributes:**

There is **no established YAML-specific convention**. Tools follow the same conventions as JSON because YAML is a superset of JSON:

| Tool | Attribute prefix | Text content key | Notes |
|------|-----------------|-----------------|-------|
| **yq** (Mike Farah) | `+@` (configurable) | `+content` (configurable) | Most popular CLI tool |
| **Site24x7** | `@` | `#text` | Online converter |
| **ddevtools** | `-` prefix | `#text` | Dash prefix variant |
| **UTL-X** | `@` | `#text` | Same as JSON (consistent) |

### Implementation status

| Change | Status | File |
|--------|--------|------|
| YAML `_attributes` block → `@` prefix inline | **Done** | `YAMLSerializer.kt` |
| JSON and YAML consistent | **Done** | Both serializers |
| `_text` → `#text` in output | **Done** | Both serializers |
| `writeAttributes` parameter added | **Done** | `JSONSerializer` + `YAMLSerializer.SerializeOptions` |
| `writeAttributes=false` default (leaf attrs dropped) | **Done** | `isXmlTextNode()` in both serializers |
| `writeAttributes=true` preserves leaf attrs | **Done** | `isXmlTextNode()` checks attributes when enabled |
| Wire `writeAttributes` to `output` declaration syntax | **Pending** | Parser + TransformCommand |
| Conformance tests for `writeAttributes=true` mode | **Pending** | Conformance suite |
| Conformance tests for default mode | **Done** | 4 passthrough tests (with/without attributes, JSON/YAML) |

### Sources

- [XML to JSON Conversion Best Practices — DevToolLab](https://devtoollab.com/blog/xml-to-json-conversion-best-practices)
- [xmljson Python library — Badgerfish/Parker/GData conventions](https://xmljson.readthedocs.io/en/latest/)
- [yq XML handling — attribute prefix conventions](https://mikefarah.gitbook.io/yq/usage/xml)
- [MuleSoft DataWeave XML format — writeAttributes](https://docs.mulesoft.com/dataweave/latest/dataweave-formats-xml)
- [MuleSoft DataWeave pass XML attributes](https://docs.mulesoft.com/dataweave/latest/dataweave-cookbook-pass-xml-attributes)
- [Converting Between XML and JSON — xml.com (foundational article)](https://www.xml.com/pub/a/2006/05/31/converting-between-xml-and-json.html)
- [Badgerfish convention](http://www.sklar.com/badgerfish/)

---

*B14 fixed April 27-28, 2026. Root cause: JSON/YAML serializers output raw UDM `_text` wrapper instead of unwrapping. YAML `_attributes` block replaced with `@` prefix inline (industry standard). `writeAttributes` option added (DataWeave-compatible, default `false`). Not a B13 regression — pre-existing since XML parser was written (October 2025).*

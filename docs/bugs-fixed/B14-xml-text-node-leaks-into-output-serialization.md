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

### Four problems with current behavior

1. **Leaf element attributes are silently dropped** — `currency="EUR"` on `<Total>` disappears in both JSON and YAML
2. **JSON and YAML use different formats** — JSON puts attributes as `@key` inline properties, YAML puts them in a `_attributes` block
3. **Non-leaf vs leaf inconsistency** — attributes preserved on parent elements (Order), dropped on leaf text elements (Total)
4. **The YAML `_attributes` block is a UTL-X invention** — not a recognized standard (see below)

### How we got to the current behavior

The **JSON serializer** was written with `@` prefix for attributes inline with other properties — this follows the widely-used Badgerfish convention. That part is correct.

The **YAML serializer** was written separately and used a `_attributes` nested block instead. This was **not based on any standard** — it was an ad-hoc design choice. There is no "YAML attribute convention" because YAML doesn't have a native concept of attributes (same as JSON). The `_attributes` block is a UTL-X invention that no other tool uses.

The **B14 fix** added `_text` unwrapping for leaf elements, which had the side effect of dropping attributes on those elements because the unwrapping replaces the entire object (including attributes) with just the text value.

### Industry Conventions (JSON)

There are 4 established conventions for XML-to-JSON attribute mapping:

| Convention | Attributes preserved | Attribute prefix | Text content key | Used by |
|-----------|---------------------|-----------------|-----------------|---------|
| **Badgerfish** | Yes | `@` | `$` | Java XML libs, .NET Json.NET |
| **Parker** | **No (dropped)** | N/A | Plain value | Simple converters |
| **GData** (Google) | Yes | None (collision risk) | `$t` | Google APIs (legacy) |
| **DataWeave** (MuleSoft) | Yes (opt-in via `writeAttributes`) | `@` | value itself | MuleSoft Anypoint |

### Industry Conventions (YAML)

There is **no established YAML-specific convention** for XML attributes. Tools that convert XML to YAML generally follow the same conventions as JSON because YAML is a superset of JSON. Notable tools:

| Tool | Attribute prefix | Text content key | Notes |
|------|-----------------|-----------------|-------|
| **yq** (Mike Farah) | `+@` (default, configurable) | `+content` (configurable) | Most popular CLI tool; uses `+` to avoid collisions |
| **Site24x7** | `@` | `#text` | Online converter |
| **ddevtools** | `-` prefix | `#text` | Dash prefix variant |
| **Most converters** | Drop attributes | Plain value | Parker-style, lossy |

**Key finding:** No tool uses a `_attributes` nested block like UTL-X currently does for YAML. The `_attributes` convention is non-standard and should be replaced.

### DataWeave approach (MuleSoft — closest competitor)

DataWeave uses `@` prefix and offers `writeAttributes=true` as an opt-in writer property. When enabled, attributes appear as `@key` properties at the same level as child elements. When disabled, attributes are accessible in expressions (`payload.Order.@id`) but don't appear in the output.

This is the closest model to what UTL-X should do — attributes always accessible via `@` in expressions, output behavior configurable.

### Proposed Solution: Configurable Attribute Mode

Make attribute output configurable via the `output` declaration. The mode determines how XML attributes appear in JSON **and** YAML output (same behavior for both formats).

#### Syntax

```utlx
%utlx 1.0
input xml
output json xmlattr=preserve
---
$input
```

#### Three modes with JSON AND YAML examples

---

**Mode 1: `xmlattr=drop`** (Parker-style — current default after B14)

Attributes on leaf elements are dropped. Attributes on non-leaf elements are dropped too. Cleanest output.

**JSON:**
```json
{
  "Order": {
    "Customer": "Alice",
    "Total": 299.99
  }
}
```

**YAML:**
```yaml
Order:
  Customer: Alice
  Total: 299.99
```

- Pros: Cleanest output, no XML artifacts in JSON/YAML
- Cons: Data loss — all attributes gone, not recoverable from output
- When to use: Consumer doesn't care about XML attributes, only element values

---

**Mode 2: `xmlattr=preserve`** (Badgerfish-inspired — recommended for enterprise XML)

All attributes preserved with `@` prefix. Leaf elements with attributes expand to include `#text` for the value. Leaf elements without attributes remain plain values. **Same format for JSON and YAML.**

**JSON:**
```json
{
  "Order": {
    "@id": "ORD-001",
    "Customer": "Alice",
    "Total": {
      "@currency": "EUR",
      "#text": 299.99
    }
  }
}
```

**YAML:**
```yaml
Order:
  '@id': ORD-001
  Customer: Alice
  Total:
    '@currency': EUR
    '#text': 299.99
```

- Pros: No data loss, `@` prefix is the de facto standard, consistent between JSON and YAML
- Cons: Leaf elements with attributes become objects (slightly more complex output)
- When to use: Enterprise XML (UBL invoices, FHIR, SWIFT ISO 20022) where attributes carry business data
- Note: `#text` is used instead of `_text` (which is internal UDM) or `$` (which conflicts with UTL-X variable syntax)

---

**Mode 3: `xmlattr=inline`** (flat — attributes as sibling properties)

Attributes flattened as `element@attr` sibling properties. Leaf elements stay as plain values. Attributes appear as separate keys.

**JSON:**
```json
{
  "Order": {
    "@id": "ORD-001",
    "Customer": "Alice",
    "Total": 299.99,
    "Total@currency": "EUR"
  }
}
```

**YAML:**
```yaml
Order:
  '@id': ORD-001
  Customer: Alice
  Total: 299.99
  Total@currency: EUR
```

- Pros: Flat structure, no nested objects for leaf elements
- Cons: Non-standard key format (`Total@currency`), may confuse consumers, key collision risk
- When to use: Flat output needed, consumer can handle custom key format

---

#### Default behavior decision

| Option | Default | Rationale |
|--------|---------|-----------|
| **`drop`** | Safe, no breaking change | Current behavior. Most JSON/YAML consumers don't expect attributes. Users map explicitly: `$input.Order.Total.@currency` |
| **`preserve`** | DataWeave-like, no data loss | Enterprise customers expect attributes preserved. Closest to MuleSoft behavior. But makes simple XML verbose |

**Recommendation: `xmlattr=drop` as default** with `preserve` available.

Rationale:
- Zero breaking change risk (current behavior)
- Most transformations map specific fields — attributes accessible via `$input.field.@attr` regardless of output mode
- Users doing format conversion with attribute-heavy XML (UBL, FHIR) can opt into `preserve`
- Follows the principle: simple by default, powerful when needed

#### Regardless of mode: fix YAML consistency

Even before implementing configurable modes, the YAML serializer should be fixed to match JSON behavior:
- **Remove the `_attributes` block** — it's a non-standard UTL-X invention
- **Use `@` prefix inline** — same as JSON serializer, same as industry convention
- This is a bug fix, not a feature — the current YAML behavior is inconsistent and non-standard

#### Implementation notes

- The `xmlattr` option is parsed from the `output` declaration header
- Passed to the JSON/YAML serializer as a configuration option
- Only affects XML-sourced UDM objects (objects with `attributes` map)
- JSON-sourced data has no attributes — unaffected
- The interpreter's `$input.field.@attr` access works regardless of the output mode
- XML-to-XML output is unaffected (attributes are native in XML)
- `#text` is the output key for text content when attributes are present (only in `preserve` mode)
- `_text` remains the internal UDM key (never appears in output after B14 fix)

#### Files to modify

| File | Change |
|------|--------|
| `formats/json/src/main/kotlin/.../json_serializer.kt` | Add `xmlAttributeMode` parameter, implement 3 modes |
| `formats/yaml/src/main/kotlin/.../YAMLSerializer.kt` | **Immediate: replace `_attributes` block with `@` prefix inline.** Then add `xmlAttributeMode` parameter |
| `modules/core/src/main/kotlin/.../parser/` | Parse `xmlattr=` from output declaration |
| `modules/cli/src/main/kotlin/.../TransformCommand.kt` | Pass option through to serializer |
| Conformance suite | Update attribute tests, add tests for each mode |
| This document | Update with final decision |

### Sources

- [XML to JSON Conversion Best Practices — DevToolLab](https://devtoollab.com/blog/xml-to-json-conversion-best-practices)
- [xmljson Python library — Badgerfish/Parker/GData conventions](https://xmljson.readthedocs.io/en/latest/)
- [yq XML handling — attribute prefix conventions](https://mikefarah.gitbook.io/yq/usage/xml)
- [MuleSoft DataWeave XML format — writeAttributes](https://docs.mulesoft.com/dataweave/latest/dataweave-formats-xml)
- [MuleSoft DataWeave pass XML attributes](https://docs.mulesoft.com/dataweave/latest/dataweave-cookbook-pass-xml-attributes)
- [Converting Between XML and JSON — xml.com (foundational article)](https://www.xml.com/pub/a/2006/05/31/converting-between-xml-and-json.html)
- [Badgerfish convention](http://www.sklar.com/badgerfish/)

---

*B14 fixed April 27, 2026. Root cause: JSON/YAML serializers output raw UDM `_text` wrapper instead of unwrapping. Not a B13 regression — pre-existing since XML parser was written (October 2025). XML attribute handling in output is a separate design decision (documented above, implementation pending).*

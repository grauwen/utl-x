# F09: Accept `#text` as Alias for `_text` in XML Serialization

**Status:** Open  
**Priority:** High  
**Created:** May 2026  
**Related:** B14 (XML text node leak fix), Chapter 29 (XML attribute design)

---

## Classification: Feature (not bug)

This is a feature, not a bug. `_text` works correctly — it has always been the internal convention. The issue is that B14 introduced `#text` as the public-facing name in JSON/YAML output, but the XML serializer doesn't accept `#text` as input. This is a gap between output and input conventions, not broken behavior.

## Problem

After B14, JSON/YAML output with `writeAttributes=true` produces `#text` for element text content:

```json
{"@listID": "UNCL1001", "#text": "380"}
```

But when a user writes the same pattern in a `.utlx` transformation body to construct XML:

```utlx
"cbc:InvoiceTypeCode": {
  "@listID": "UNCL1001",
  "#text": "380"
}
```

The XML serializer does NOT recognize `#text` — it only recognizes `_text`. The `#text` is serialized as a child element named `#text` instead of being treated as the element's text content.

This breaks the round-trip expectation: what UTL-X outputs in JSON, it should accept as input for XML construction.

## Change Required

The XML serializer should accept BOTH `_text` and `#text` as text content markers:

```kotlin
// In xml_serializer.kt — where _text is detected:
val isTextContent = key == "_text" || key == "#text"
```

Similarly, any code that checks for `_text` (e.g., `isXmlTextNode()`) should also check for `#text`.

### Files to modify

| File | Change |
|------|--------|
| `formats/xml/src/main/kotlin/.../xml_serializer.kt` | Accept `#text` alongside `_text` when detecting text content |
| `formats/json/src/main/kotlin/.../json_serializer.kt` | Already outputs `#text` (B14) — no change needed |
| `formats/yaml/src/main/kotlin/.../YAMLSerializer.kt` | Already outputs `#text` (B14) — no change needed |

### Documentation

- `#text` becomes the documented standard (matches JSON/YAML output, matches W3C DOM convention)
- `_text` continues to work as a legacy alias (no breaking change)
- Book chapters 22, 29, 47 should use `#text` in examples

## Test Cases

1. `{"@attr": "val", "#text": "content"}` → XML: `<elem attr="val">content</elem>` ✓
2. `{"@attr": "val", "_text": "content"}` → same output (backward compatible) ✓
3. JSON → XML → JSON round-trip with `writeAttributes=true` preserves `#text` ✓
4. Existing conformance tests with `_text` still pass ✓

## Effort

~0.5 day (small change, well-scoped).

---

*Feature F09. May 2026.*
*Ensures round-trip consistency: what UTL-X outputs in JSON (`#text`), it accepts when constructing XML.*

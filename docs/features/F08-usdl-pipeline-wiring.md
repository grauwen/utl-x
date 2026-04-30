# F08: USDL Pipeline Wiring — Schema-to-Schema Conversion via USDL

**Status:** Missing Feature (building blocks exist, pipeline not wired)  
**Priority:** High  
**Created:** April 2026  
**Related:** Ch11 (USDL), Ch27 (Schema Formats), Ch10 (Schema-to-Schema Mapping)

---

## The Problem

The book and documentation describe a powerful schema conversion pipeline:

```utlx
%utlx 1.0
input xsd
output yaml %usdl 1.0
---
$input
```

**Expected behavior:** Read an XSD schema, convert it to USDL representation (with `%namespace`, `%types`, `%fields` directives), output as human-readable YAML.

**Actual behavior:** The XSD is parsed as XML structure (preserving `xs:element`, `xs:complexType` XML nodes) and output as raw XML-in-YAML. No USDL conversion occurs. The `%usdl 1.0` dialect tag is parsed but ignored.

This affects ALL schema format combinations:

| Pipeline | Expected | Actual |
|----------|----------|--------|
| `input xsd` → `output yaml %usdl 1.0` | USDL YAML with `%types` | Raw XML-as-YAML |
| `input xsd` → `output json %usdl 1.0` | USDL JSON with `%types` | Raw XML-as-JSON |
| `input xsd` → `output jsch` | JSON Schema 2020-12 | Error: "not valid JSON Schema" |
| `input xsd` → `output avro` | Avro schema JSON | Error (expected) |
| `input xsd` → `output proto` | .proto definition | Error (expected) |
| `input jsch` → `output xsd` | XSD from JSON Schema | Similar issue |
| Any Tier 2 → Any Tier 2 | Schema conversion | Fails |

## Root Cause

The building blocks exist but the pipeline doesn't connect them:

### What exists:

1. **Header parser** — parses `%usdl 1.0` into `FormatSpec.dialect` ✅
2. **Schema parsers** — `XSDParser`, `JSONSchemaParser`, `AvroSchemaParser`, `ProtobufSchemaParser`, `EDMXParser`, `TableSchemaParser` — each has a `parse()` method ✅
3. **Schema parser `toUSDL()` methods** — `JSONSchemaParser.toUSDL()`, `XSDParser` (via enhancer), etc. — convert the parsed schema into USDL-structured UDM with `%types`, `%fields`, `%namespace` ✅
4. **Schema serializers** — `XSDSerializer`, `JSONSchemaSerializer`, `AvroSchemaSerializer`, `ProtobufSchemaSerializer`, `EDMXSerializer` — each can serialize USDL-structured UDM to native format ✅
5. **YAML/JSON serializers** — can serialize any UDM to YAML/JSON ✅

### What's missing:

**TransformationService does not invoke `toUSDL()` when parsing schema formats.**

The current pipeline in `TransformationService.parseInput()`:
```kotlin
"xsd" -> XSDParser(data, arrayHints).parse()
```

This returns the XSD parsed as XML structure. It does NOT call any `toUSDL()` conversion. The result is a UDM tree that looks like XML (`xs:element`, `xs:complexType`), not like USDL (`%types`, `%fields`).

**TransformationService does not check the `%usdl` dialect on output.**

The `formatSpec.dialect` value is stored in the AST but never read during serialization. The YAML serializer and JSON serializer have no knowledge of USDL — they serialize whatever UDM they receive.

## Proposed Solution

### Step 1: Add USDL conversion to schema input parsing

In `TransformationService.parseInput()`, when the input format is a Tier 2 schema format, invoke the `toUSDL()` conversion:

```kotlin
"xsd" -> {
    val parser = XSDParser(data, arrayHints)
    val xmlUdm = parser.parse()
    parser.toUSDL(xmlUdm)  // Convert to USDL-structured UDM
}
"jsch" -> {
    val parser = JSONSchemaParser(data)
    parser.toUSDL()  // Parse and convert to USDL
}
// Similar for avro, proto, osch, tsch
```

After this step, the UDM tree has `%types`, `%fields`, `%namespace` properties — the USDL representation.

### Step 2: Schema serializers consume USDL input

The schema serializers (`XSDSerializer`, `JSONSchemaSerializer`, etc.) already expect USDL-structured input. No changes needed here — they check for `%types` and generate the native format.

### Step 3: YAML/JSON with `%usdl` — pass through

When output is `yaml %usdl 1.0` or `json %usdl 1.0`, the USDL-structured UDM is serialized as-is. The `%` prefixed properties (`%types`, `%fields`, `%namespace`) become regular YAML/JSON properties. No special USDL handling needed in the serializer — the USDL structure IS the output.

### Step 4: Tier 2 to Tier 2 conversion

With Step 1, Tier 2 → Tier 2 works automatically:

```
input xsd → toUSDL() → USDL-structured UDM → JSONSchemaSerializer → output jsch
input avro → toUSDL() → USDL-structured UDM → ProtobufSchemaSerializer → output proto
```

The USDL representation is the universal intermediate — exactly as designed in Ch11.

## What Changes

| File | Change |
|------|--------|
| `TransformationService.kt` | Add `toUSDL()` call for Tier 2 input formats |
| Schema parsers | Verify `toUSDL()` methods exist and produce correct USDL (most already do) |
| Nothing else | Serializers already handle USDL input; YAML/JSON just serialize the UDM |

## Impact

### What starts working:

- `input xsd` → `output yaml %usdl 1.0` (human-readable schema dump)
- `input xsd` → `output json %usdl 1.0` (machine-readable schema dump)
- `input xsd` → `output jsch` (XSD to JSON Schema conversion)
- `input xsd` → `output avro` (XSD to Avro conversion)
- `input xsd` → `output proto` (XSD to Protobuf conversion)
- All 30 Tier 2 → Tier 2 combinations (6 formats × 5 target formats)
- All 12 Tier 2 → Tier 1 with `%usdl` combinations (6 formats × 2 carriers: YAML, JSON)
- `parseXSDSchema()`, `parseJSONSchema()` stdlib functions (these call `toUSDL()` separately — verify they still work)

### What the book documents but doesn't work today:

- Ch11: all `output yaml %usdl 1.0` examples
- Ch23: JSON Schema reading/writing examples
- Ch26: EDMX → YAML USDL example
- Ch27: all schema-to-schema conversion examples
- Ch10: nested schema resolution examples

### Book impact after fix:

All existing examples become correct. No book changes needed — the book describes the intended behavior, which is what F08 implements.

## Effort Estimate

| Task | Effort |
|------|--------|
| Wire `toUSDL()` in TransformationService for all 6 schema formats | 1 day |
| Verify/fix `toUSDL()` methods produce correct USDL | 1-2 days |
| Conformance tests for all Tier 2 → Tier 2 combinations | 1 day |
| Conformance tests for Tier 2 → YAML/JSON with `%usdl` | 0.5 day |
| Verify stdlib schema functions still work | 0.5 day |
| **Total** | **4-5 days** |

## Priority

**High.** This is a core capability described extensively in the book and documentation. Without it, the entire schema-to-schema conversion story and the USDL chapter are aspirational rather than functional. Every `output yaml %usdl 1.0` example in the book fails silently (produces XML-as-YAML instead of USDL-structured YAML).

## Test Plan

1. XSD → YAML `%usdl` → verify `%types`, `%fields` in output
2. XSD → JSON Schema → verify valid JSON Schema 2020-12
3. JSON Schema → XSD → verify valid XSD
4. Avro → Protobuf → verify valid .proto
5. All 30 Tier 2 → Tier 2 pairs → verify no errors
6. Round-trip: XSD → USDL YAML → XSD → compare with original
7. `parseXSDSchema()` stdlib → verify returns USDL structure
8. `renderJSONSchema()` stdlib → verify accepts USDL input

---

*Feature document F08. April 2026.*

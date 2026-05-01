# F08: USDL Enrichment — Schema Input with Dual Access

**Status:** Design finalized — implementation pending  
**Priority:** High  
**Created:** April 2026  
**Updated:** May 2026 (enrichment approach replaces conversion approach)  
**Related:** Ch12 (USDL), Ch28 (Schema Formats), Ch11 (Schema-to-Schema Mapping)  
**Design doc:** `docs/design/usdl-enrichment-design.md`

---

## Scope

F08 covers the **INPUT side only** — enriching Tier 2 schema input with USDL `%` properties alongside the raw UDM structure.

The OUTPUT side (USDL → native schema) is **already implemented** — all 6 Tier 2 serializers already consume `%types`, `%fields`, `%kind` and produce native schema output (XSD, JSON Schema, Avro, Protobuf, EDMX, Table Schema).

## The Approach: Enrichment, Not Conversion

USDL properties (`%types`, `%fields`, `%kind`) are ADDED alongside the raw UDM structure — not replacing it. The `%` prefix guarantees no collision with any raw format property.

After enrichment, `$input` has BOTH views:

```utlx
$input["xs:element"].@name              // raw XSD access — works
$input["%types"].Order["%fields"]       // USDL access — also works
```

See `docs/design/usdl-enrichment-design.md` for the full design rationale and evolution.

## All 6 Tier 2 Parsers Need Changes

| Parser | `parse()` currently returns | Change needed | Has `toUSDL()`? |
|--------|---------------------------|---------------|-----------------|
| **XSD** | Raw XML structure only | ADD `%` properties from `toUSDL()` | Yes ✅ |
| **JSON Schema** | Raw JSON structure only | ADD `%` properties from `toUSDL()` | Yes ✅ |
| **Avro** | Raw JSON structure only | ADD `%` properties from `toUSDL()` | Yes ✅ |
| **Table Schema** | Raw JSON structure only | ADD `%` properties from `toUSDL()` | Yes ✅ |
| **Protobuf** | USDL only (raw lost) | KEEP raw .proto structure alongside USDL | No (built-in) |
| **EDMX** | USDL only (raw lost) | KEEP raw EDMX XML alongside USDL | No (built-in) |

Two directions of change:
- **4 parsers (XSD, JSCH, Avro, TSCH):** currently raw-only → add USDL enrichment
- **2 parsers (Protobuf, EDMX):** currently USDL-only → also preserve raw structure

After F08, ALL 6 parsers produce enriched UDM with both raw + USDL `%` properties.

## Implementation Per Parser

### Group 1: Add USDL to raw (XSD, JSON Schema, Avro, Table Schema)

These parsers have separate `parse()` (raw) and `toUSDL()` (USDL) methods. The change: merge both results.

```kotlin
// Before:
fun parse(): UDM = parseRawStructure()     // raw only

// After:
fun parse(): UDM {
    val raw = parseRawStructure()
    val usdl = toUSDL(raw)
    // Merge: raw properties + USDL % properties (no collision due to % prefix)
    val enrichedProperties = raw.properties +
        usdl.properties.filter { it.key.startsWith("%") }
    return UDM.Object(enrichedProperties, raw.attributes, raw.name, raw.metadata)
}
```

### Group 2: Add raw to USDL (Protobuf, EDMX)

These parsers currently discard the raw structure when producing USDL. The change: keep both.

```kotlin
// Protobuf — before:
fun parse(protoSource: String): UDM {
    val protoFile = parseProtoText(protoSource)
    return buildUSDLFromProtoFile(protoFile)    // USDL only, raw lost
}

// Protobuf — after:
fun parse(protoSource: String): UDM {
    val protoFile = parseProtoText(protoSource)
    val usdl = buildUSDLFromProtoFile(protoFile)
    val rawProperties = buildRawProperties(protoFile)  // NEW: preserve raw .proto structure
    return UDM.Object(rawProperties + usdl.properties, ...)
}
```

## What the Output Side Already Has

All 6 Tier 2 serializers already detect `%types` and switch to USDL mode:

```kotlin
// Every serializer has this pattern:
when {
    udm.properties.containsKey("%types") -> SerializationMode.UNIVERSAL_DSL
    // ... other modes
}
```

This means schema creation from scratch ALREADY WORKS:

```utlx
%utlx 1.0
input json
output xsd
---
{
  "%namespace": "com.example",
  "%types": {
    "Order": {
      "%kind": "object",
      "%fields": {
        "id": { "%type": "string", "%required": true }
      }
    }
  }
}
```

`echo '{}' | utlx schema.utlx` → produces valid XSD. The input is a dummy trigger; the body is a literal USDL definition.

## What F08 Enables (After Implementation)

### Schema-to-schema conversion:
```utlx
%utlx 1.0
input xsd
output jsch
---
// $input has raw XSD + USDL enrichment
// Output serializer sees %types → produces JSON Schema
$input
```

### Schema inspection with both views:
```utlx
%utlx 1.0
input xsd
output json
---
{
  pattern: $input.^xsdPattern,
  rawElementCount: count($input["xs:element"]),
  types: $input["%types"],
  fieldCount: count(keys($input["%types"]))
}
```

### Schema enrichment (add constraints):
```utlx
%utlx 1.0
input jsch
output jsch
---
{
  ...$input,
  "%types": mapValues($input["%types"], (typeDef) -> {
    ...typeDef,
    "%fields": mapValues(typeDef["%fields"], (field) -> {
      ...field,
      "%description": field["%description"] ?? concat("Field: ", field["%type"])
    })
  })
}
```

## Enrichment Diagnostics (`%_diagnostics`)

Enrichment can fail partially. A schema may parse to UDM but have features that can't be modeled in USDL. When this happens, the enrichment records diagnostics:

```json
{
  "%_diagnostics": {
    "%_status": "partial",
    "%_warnings": [
      {"path": "xs:element[@name='Payload']", "issue": "xs:any wildcard — type set to 'any'"},
      {"path": "xs:import[@namespace='urn:ext']", "issue": "External schema not available"}
    ],
    "%_unsupportedFeatures": ["xs:redefine", "xs:any"],
    "%_enrichmentCoverage": "85%"
  }
}
```

### Known USDL Enrichment Edge Cases

| Schema feature | UDM | USDL | Fallback |
|---------------|-----|------|----------|
| `xs:any` wildcard | ✓ | ⚠ | `%type: "any"` + warning |
| `xs:redefine` (circular) | ✓ | ⚠ | Skip circular types + warning |
| `xs:include` (same namespace) | ✓ | ✓ if file available | Merge included types into `%types` |
| `xs:import` (different namespace) | ✓ | ⚠ if file not available | Type refs as strings + warning |
| Chameleon include (no namespace) | ✓ | ✓ | Adopts parent namespace |
| JSON Schema `$dynamicRef` | ✓ | ⚠ | Preserve as string ref + warning |
| Avro nested unions | ✓ | ⚠ | Flatten to first non-null type + warning |

### Principle: Enrichment Failure Never Blocks

If `toUSDL()` throws an exception, catch it and:
1. Set `%_diagnostics.%_status` to `"failed"`
2. Preserve the raw UDM unchanged (developer can still use `$input["xs:element"]`)
3. `$input["%types"]` returns null (no USDL available)
4. IDE shows UDM ✓ USDL ✗

If `toUSDL()` succeeds partially:
1. Set `%_diagnostics.%_status` to `"partial"`
2. Include whatever `%types` could be modeled
3. Record warnings for what couldn't
4. IDE shows UDM ✓ USDL ⚠

## IDE Impact

### Three-Stage Status for Tier 2 Input

| Format ✓ | UDM ✓ | USDL | Meaning |
|----------|-------|------|---------|
| ✓ | ✓ | ✓ | Fully parsed and enriched |
| ✓ | ✓ | ⚠ | Parsed OK, USDL partial — hover for details |
| ✓ | ✓ | ✗ | Parsed OK, USDL failed — raw access only |
| ✗ | — | — | Format parse error |

For Tier 1 input (JSON, XML, CSV, YAML): no USDL indicator shown.

### Tree Browser

USDL nodes appear alongside raw nodes with visual distinction:

```
$input
├── xs:element              ← raw (format icon)
│   └── @name: "Customer"
├── %types                  ← USDL (% icon, blue)
│   └── Order
│       └── %fields
│           └── Customer: {%type: "string"}
└── %_diagnostics           ← enrichment status
    └── %_status: "complete"
```

Recommendations: toggle (Raw / USDL / Both), USDL collapsed by default, autocompletion shows both paths.

## Effort Estimate

| Task | Effort |
|------|--------|
| XSD parser: merge toUSDL() into parse() with try/catch | 0.5 day |
| JSON Schema parser: same | 0.5 day |
| Avro parser: same | 0.5 day |
| Table Schema parser: same | 0.5 day |
| Protobuf parser: preserve raw alongside USDL | 1 day |
| EDMX parser: preserve raw alongside USDL | 1 day |
| `%_diagnostics` reporting infrastructure | 1 day |
| Update conformance tests | 1 day |
| Update book ch12 | 0.5 day |
| IDE: USDL checkmark + tree styling | 1-2 days |
| **Total** | **7-8 days** |

## Test Plan

1. `input xsd` → `$input["xs:element"]` works (raw) AND `$input["%types"]` works (USDL)
2. `input jsch` → same dual access
3. `input avro` → same dual access
4. `input proto` → same dual access
5. `input osch` → same dual access
6. `input tsch` → same dual access
7. `input xsd` → `output jsch` → passthrough produces valid JSON Schema
8. `input jsch` → `output xsd` → passthrough produces valid XSD
9. `input avro` → `output proto` → cross-format conversion
10. Schema creation from scratch → all 6 output formats
11. XSD with `xs:any` → `%_diagnostics` shows warning, `%type: "any"` used
12. XSD with unresolvable `xs:import` → `%_diagnostics` shows warning, types preserved as strings
13. Broken enrichment → `%_diagnostics.%_status: "failed"`, raw UDM still works
14. Existing conformance tests still pass

---

*Feature document F08. Updated May 2026.*
*Key insight: USDL enrichment (add % alongside raw), not conversion (replace raw with %).*
*Output side already done. F08 = input side enrichment for all 6 Tier 2 parsers.*
*Enrichment is best-effort with `%_diagnostics` — failure never blocks the transformation.*

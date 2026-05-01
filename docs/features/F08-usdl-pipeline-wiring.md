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

## Effort Estimate

| Task | Effort |
|------|--------|
| XSD parser: merge toUSDL() into parse() | 0.5 day |
| JSON Schema parser: merge toUSDL() into parse() | 0.5 day |
| Avro parser: merge toUSDL() into parse() | 0.5 day |
| Table Schema parser: merge toUSDL() into parse() | 0.5 day |
| Protobuf parser: preserve raw alongside USDL | 1 day |
| EDMX parser: preserve raw alongside USDL | 1 day |
| Update conformance tests (passthrough output includes %) | 1 day |
| Update book ch12 (USDL is enrichment) | 0.5 day |
| **Total** | **5-6 days** |

## Test Plan

1. `input xsd` → verify `$input["xs:element"]` works (raw) AND `$input["%types"]` works (USDL)
2. `input jsch` → same dual access
3. `input avro` → same dual access
4. `input proto` → same dual access (raw .proto structure + USDL)
5. `input osch` → same dual access (raw EDMX XML + USDL)
6. `input tsch` → same dual access
7. `input xsd` → `output jsch` → passthrough produces valid JSON Schema (serializer reads `%types`)
8. `input jsch` → `output xsd` → passthrough produces valid XSD
9. `input avro` → `output proto` → cross-format schema conversion
10. Schema creation from scratch (body literal with `%types`) → all 6 output formats
11. Existing conformance tests still pass (raw access unchanged)

---

*Feature document F08. Updated May 2026.*
*Key insight: USDL enrichment (add % alongside raw), not conversion (replace raw with %).*
*Output side already done. F08 = input side enrichment for all 6 Tier 2 parsers.*

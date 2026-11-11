# USDL Enrichment Design — Schema-Aware UDM

**Date:** May 2026  
**Status:** Design decision — enrichment approach selected  
**Related:** F08 (USDL pipeline wiring), Ch12 (USDL chapter in book)

---

## Key Insight: Enrichment, Not Conversion

The original F08 design treated USDL as a REPLACEMENT for the raw UDM tree — "convert XSD to USDL." This was wrong. USDL should be an ENRICHMENT — added alongside the raw UDM structure, not replacing it.

The `%` prefix on USDL properties (`%types`, `%fields`, `%kind`) guarantees zero collision with any raw format property name. XML elements never start with `%`. JSON keys never start with `%`. Avro field names never start with `%`. The `%` namespace is reserved for USDL — it can safely coexist with any raw structure on the same UDM tree.

### Before (wrong — conversion):
```
XSD bytes → parse → USDL tree (%types, %fields)
                     Raw XSD structure is GONE
```

### After (correct — enrichment):
```
XSD bytes → parse → UDM tree with BOTH:
                     - Raw: xs:element, xs:complexType (original structure)
                     - USDL: %types, %fields, %kind (schema abstraction)
```

Both views on the same `$input`:
```utlx
$input["xs:element"].@name              // raw XSD access — still works
$input["%types"].Order["%fields"]       // USDL access — also works
$input.^xsdPattern                      // metadata — also works
```

No information loss. No separate variables. No conversion functions. No tags.

## Design Evolution (How We Got Here)

| Phase | Approach | Problem |
|-------|----------|---------|
| Original F08 | Convert input to USDL (replace raw) | Loses raw structure |
| Attempt 1 | Convert only when `%usdl` on output | Wrong — output declaration controls input behavior |
| Attempt 2 | Convert only when `%usdl` on input | Better direction — but still replaces raw |
| Attempt 3 | `toUSDL()` function (explicit) | Both views available, but ugly function dependency on Tier 2 |
| Attempt 4 | Dual variables `$input` + `%$input` | Both views, but requires parser changes |
| Attempt 5 | Lazy on-demand conversion | Elegant but magic hidden computation |
| **Final** | **Enrich — add USDL alongside raw** | **Both views on same `$input`, zero information loss** |

The key insight: USDL properties use `%` prefix → guaranteed no collision → can coexist with any raw structure → enrichment, not conversion.

## UDM and USDL Relationship (Corrected)

**USDL IS a layer on top of UDM.** Not a replacement, not a superset, not a separate tree. USDL properties are additional entries in the UDM Object's property map, distinguished by the `%` prefix.

```
UDM Object (after Tier 2 parse with enrichment):
{
  properties: {
    // Raw structure (format-specific, always present):
    "xs:element" → UDM.Object(...)
    "xs:complexType" → UDM.Object(...)

    // USDL enrichment (format-agnostic, % prefixed, added by parser):
    "%types" → UDM.Object(...)
    "%namespace" → UDM.Scalar(...)
    "%xsdPattern" → UDM.Scalar(...)
  },
  attributes: { ... },
  metadata: { "__schemaType" → "xsd-schema" }
}
```

## When Does Enrichment Happen?

### Tier 2 (Schema Formats): Always Enriched

When the input is a schema format, the parser adds USDL `%` properties alongside the raw structure:

| Input | Raw properties (preserved) | USDL properties (added) |
|-------|---------------------------|------------------------|
| XSD | `xs:element`, `xs:complexType`, `xs:sequence` | `%types`, `%fields`, `%kind`, `%namespace` |
| JSON Schema | `properties`, `required`, `$defs`, `type` | `%types`, `%fields`, `%kind` |
| Avro | `type`, `name`, `fields`, `namespace` | `%types`, `%fields`, `%kind`, `%namespace` |
| Protobuf | (raw .proto structure) | `%types`, `%fields`, `%fieldNumber` |
| EDMX | (raw EDMX XML structure) | `%types`, `%fields`, `%entityType` |
| Table Schema | `fields`, `primaryKey`, `foreignKeys` | `%types`, `%fields`, `%kind` |

### Tier 1 (Data Formats): No Enrichment

JSON, XML, CSV, YAML, OData are DATA, not schemas. No USDL `%` properties are added. Accessing `$input["%types"]` returns `null`.

Future possibility: `inferSchema($input)` could generate USDL from data values — but that's a separate feature, not enrichment.

## Implementation Options

### Option A: Eager Enrichment (at Parse Time)

The Tier 2 parser always produces both raw and USDL properties at parse time:

```kotlin
fun parse(): UDM {
    val raw = parseRawStructure()
    val usdl = buildUSDLProperties(raw)
    return UDM.Object(
        raw.properties + usdl,  // merge: raw + USDL % properties
        raw.attributes, raw.name, raw.metadata
    )
}
```

**Pros:**
- Simple — parse once, both views available immediately
- No lazy complexity, no caching issues
- `$input["%types"]` always works for Tier 2
- Predictable performance

**Cons:**
- Slightly larger UDM tree (raw + USDL properties)
- If user only needs raw access, USDL enrichment is wasted work
- For very large schemas (3MB+ EDMX), enrichment adds overhead

### Option B: Lazy Enrichment (on First `%` Access) ← User's Preference

The parser produces raw UDM only. USDL `%` properties are computed on first `%` access and cached:

```kotlin
// In the interpreter, when property not found:
if (name.startsWith("%") && obj.hasMetadata("schemaType")) {
    // First % access: compute USDL, merge into UDM, cache
    val usdlProps = computeUSDL(obj)
    obj.enrichWith(usdlProps)  // add % properties to the map
    return obj.properties[name]
}
```

**Pros:**
- Zero overhead if user never accesses `%` properties
- Enrichment happens once, cached for subsequent access
- No `%usdl` tag needed — completely automatic
- User doesn't even need to know about enrichment — it just works

**Cons:**
- First `%` access is slower (one-time computation + cache)
- UDM Objects are currently immutable — need mutable cache or wrapper
- Format-specific knowledge needed to choose the right enricher
- Debugging: "why is first access slow?"

**Implementation detail:** The UDM.Object could hold a lazy `usdlCache: Map<String, UDM>?` that is computed on first `%` access. Once computed, all `%` lookups hit the cache. The cache is per-UDM-object, so multiple enriched schemas in the same transformation each have their own cache.

### Option C: Opt-In via `%usdl` on Input

```utlx
input xsd %usdl 1.0    // enrichment happens (eager)
input xsd               // raw only, no enrichment
```

**Pros:**
- Explicit — user decides
- Clear performance characteristics

**Cons:**
- Users forget to add `%usdl` → wonder why `%types` is null
- Extra syntax that could be automatic

## Recommended Approach

**For v1: Option A (Eager Enrichment)**

Always enrich Tier 2 input with USDL at parse time. Schemas are typically kilobytes — the overhead of adding `%` properties is negligible. Having `%types` always available prevents the "forgot to add `%usdl`" problem.

**For v2: Consider Option B (Lazy)**

If profiling shows eager enrichment is a problem for very large schemas, switch to lazy. The user-facing behavior is identical — only the internal timing changes. The lazy approach is more elegant but requires mutable caching in the currently-immutable UDM.

## The `%usdl` Tag — What Happens to It?

With automatic enrichment, `%usdl 1.0` on input or output is no longer needed as a TRIGGER. It could be kept as:

- **Documentation** — signals "this transformation works with schema data"
- **Validation** — engine warns if `%usdl` is on Tier 1 input (no schema to enrich)
- **Future** — could control enrichment level (Tier 1 only? Tier 1+2? Include Tier 3 format-specific?)

But it does NOT trigger conversion or enrichment — that happens automatically.

## The `toUSDL()` Function — Still Needed?

**For Tier 2 input:** No. `$input["%types"]` is always available after enrichment.

**For extracting USDL-only view:** Maybe. A convenience function to strip raw properties and keep only `%` properties:
```utlx
// Manual approach (works today):
filterEntries($input, (key, value) -> startsWith(key, "%"))

// Convenience function (cleaner):
toUSDL($input)   // returns only % properties
```

**For Tier 1 → schema inference:** A future `inferSchema($input)` function could generate USDL from data values (`{"name": "Alice"}` → `{%type: "string"}`). This is separate from enrichment.

**Decision:** Do NOT add `toUSDL()` for v1. The enrichment is automatic, and `filterEntries` handles the "USDL-only view" case. Add `inferSchema()` as a separate future feature when demand exists.

## Impact on Conformance Tests

With eager enrichment, existing conformance tests that `input xsd` and navigate raw structure CONTINUE TO WORK — the raw properties are still there. The only difference: the output will also include `%` properties if the user does `$input` passthrough.

Tests that verify exact output would need updating to include `%` properties. Tests that access specific raw properties (`$input["xs:element"]`) are unchanged.

## Implementation Plan (F08 Revised)

1. **Modify `XSDParser.parse()`** — after building raw UDM, call `toUSDL()` and merge `%` properties into the raw tree
2. **Modify `JSONSchemaParser.parse()`** — same pattern
3. **Modify `AvroSchemaParser.parse()`** — same pattern
4. **Modify `TableSchemaParser.parse()`** — same pattern
5. **`ProtobufSchemaParser`** — already returns USDL, needs to also keep raw .proto structure
6. **`EDMXParser`** — already returns USDL, needs to also keep raw EDMX XML structure
7. **Update conformance tests** — tests with `$input` passthrough may include `%` properties in output
8. **Update book ch12** — USDL is enrichment on top of UDM, not a separate representation

## YAML/JSON as USDL Carrier Formats

Tier 1 formats (YAML, JSON) don't need USDL enrichment on INPUT — but they naturally ACT AS CARRIERS for USDL content on OUTPUT. This enables a powerful round-trip:

```
1. XSD file
   ↓  input xsd (Tier 2 — parser enriches with %types)
2. UDM with raw xs:element + USDL %types
   ↓  output yaml (Tier 1 — serializes everything including % properties)
3. Human-readable YAML with %types, %fields alongside raw structure
   ↓  (developer edits the YAML in a text editor — adds fields, changes types)
4. Modified YAML file
   ↓  input yaml (Tier 1 — reads %types as regular YAML data)
5. UDM with %types (from YAML content, not from enrichment)
   ↓  output xsd (Tier 2 serializer sees %types → generates XSD)
6. New XSD file — reflecting the manual edits
```

**YAML is the human-readable editing layer for schemas.** Read any schema as YAML, edit in any text editor, write back as any schema format.

### Why This Works

The Tier 2 serializer doesn't care HOW `%types` got into the UDM. It just checks: "does this UDM have `%types`?" If yes → generate native schema. The `%types` could come from:

1. **Parser enrichment** — XSD parser added `%types` alongside raw structure
2. **YAML/JSON data** — the file happened to contain `%types` as a data property
3. **Transformation body** — developer wrote `%types` as a literal object

All three produce identical schema output. The serializer is source-agnostic.

### Tier 1 Summary (Corrected)

| | INPUT | OUTPUT |
|---|---|---|
| **Tier 1 as data** | No enrichment — just data | Just serializes — no special handling |
| **Tier 1 as USDL carrier** | `%types` in YAML/JSON content flows through as data | Serializer doesn't care — `%` properties are regular properties |
| **Tier 2 serializer consuming Tier 1 output** | N/A | Detects `%types` → generates native schema |

Tier 1 formats are TRANSPARENT to USDL — they carry `%` properties without knowing or caring that they're USDL. The intelligence is in the Tier 2 serializers (which detect `%types`) and the Tier 2 parsers (which add `%types` via enrichment).

## The `%usdl 1.0` Tag — Final Status

With automatic enrichment (Tier 2 parsers) and automatic detection (Tier 2 serializers), the `%usdl 1.0` tag has **no remaining functional purpose**:

| Scenario | `%usdl` needed? | Why not? |
|----------|----------------|----------|
| Tier 2 input enrichment | No | Automatic — parser always enriches |
| Tier 2 output generation | No | Serializer auto-detects `%types` |
| Tier 1 as USDL carrier (input) | No | `%types` is just data — flows through |
| Tier 1 as USDL carrier (output) | No | Serializer just writes `%` properties as data |
| Schema creation from scratch | No | Write `%types` in body — serializer picks it up |

**The `%usdl 1.0` tag does nothing.** Every use case is handled by:
- Tier 2 parsers enriching automatically
- Tier 2 serializers detecting `%types` automatically
- Tier 1 formats carrying `%` properties transparently

### What to Do with `%usdl 1.0`

**Option 1: Keep as documentation.** The header parser already parses it. It's stored in `FormatSpec.dialect` and harmlessly ignored. Keeping it lets users signal intent: "this transformation works with schema data." The book can show it as an optional annotation.

**Option 2: Deprecate.** Log a warning: "`%usdl 1.0` is no longer needed — USDL enrichment is automatic for Tier 2 input." Remove from book examples over time.

**Option 3: Repurpose.** Use the `%usdl` tag for future features:
- Control enrichment level (Tier 1 directives only? Include Tier 3 format-specific?)
- Enable schema inference for Tier 1 data (`input json %usdl 1.0` → infer schema from JSON data)
- Version the USDL directive set (1.0 vs future 2.0 with new directives)

**Recommendation:** Option 1 for now (keep, harmless). Option 3 opens interesting doors for the future — especially schema inference for Tier 1 data, which would give `%usdl` a real purpose again.

## USDL Enrichment Can Fail — Partial Enrichment and Diagnostics

### When Enrichment Fails

USDL enrichment is a SEPARATE step from UDM parsing. A schema can parse correctly to UDM but fail (fully or partially) to produce USDL. Real-world cases:

| Schema feature | UDM behavior | USDL behavior |
|---------------|-------------|--------------|
| `xs:any` wildcard | UDM captures the XML node ✓ | USDL cannot model "any type" — what `%type`? |
| `xs:redefine` (circular) | UDM is a tree, captures structure ✓ | USDL type resolution may hit infinite loop |
| `xs:include` / `xs:import` (nested schemas) | UDM captures the XML reference nodes ✓ | USDL must resolve external files — may fail if files not available |
| JSON Schema `$dynamicRef` | UDM has the JSON property ✓ | USDL can't follow dynamic references at parse time |
| Avro unions-of-unions (deeply nested) | UDM captures the JSON arrays ✓ | USDL type mapping becomes ambiguous |
| Protobuf `oneof` with `map` inside | UDM structure fine ✓ | USDL field modeling edge case |
| Any schema feature the converter doesn't support yet | UDM always works ✓ | USDL converter throws or skips |

The key principle: **enrichment failure should NEVER block the transformation.** The raw UDM is always valid. USDL enrichment is best-effort — produce what you can, report what you couldn't.

### The `%_diagnostics` Directive

When enrichment encounters issues, they should be recorded in a special `%_diagnostics` property on the UDM:

```json
{
  "xs:element": { ... },
  "%types": { ... },
  "%_diagnostics": {
    "%_status": "partial",
    "%_warnings": [
      {"path": "xs:element[@name='Payload']", "issue": "xs:any wildcard cannot be modeled as USDL type", "fallback": "%type set to 'any'"},
      {"path": "xs:import[@namespace='urn:external']", "issue": "External schema not available for resolution", "fallback": "Type references preserved as strings"}
    ],
    "%_unsupportedFeatures": ["xs:redefine", "xs:any"],
    "%_enrichmentCoverage": "85%"
  }
}
```

The developer can then:
- Check `$input["%_diagnostics"]["%_status"]` — `"complete"` or `"partial"`
- Read `$input["%_diagnostics"]["%_warnings"]` — what couldn't be modeled and what fallback was used
- Check `$input["%_diagnostics"]["%_unsupportedFeatures"]` — which schema features are not USDL-modeled

### IDE Impact

The IDE currently shows two checkmarks for Tier 2 input: Format ✓ and UDM ✓. With enrichment, add a third:

| Input | Format | UDM | USDL | Meaning |
|-------|--------|-----|------|---------|
| JSON data | ✓ | ✓ | — | Tier 1: no USDL applicable |
| XSD (simple) | ✓ | ✓ | ✓ | Fully parsed + fully enriched |
| XSD (with `xs:any`) | ✓ | ✓ | ⚠ | Parsed OK, USDL partial — some types modeled as `any` |
| XSD (with unresolved imports) | ✓ | ✓ | ⚠ | Parsed OK, USDL partial — external types unresolved |
| XSD (broken XML) | ✗ | — | — | Format error — nothing else applies |

The ⚠ state means: "enrichment worked but with warnings." The developer can still use `%types` for the parts that worked. Hovering over ⚠ shows the diagnostics.

### Tree Browser with USDL

The UDM tree browser shows both raw and USDL nodes:

```
$input
├── xs:element              ← raw XSD (format icon)
│   ├── @name: "Customer"
│   └── @type: "xs:string"
├── xs:complexType          ← raw XSD
│   └── xs:sequence
├── %types                  ← USDL enrichment (% icon, different color)
│   └── Order
│       ├── %kind: "structure"
│       └── %fields
│           ├── Customer: {%type: "string"}
│           └── Total: {%type: "decimal"}
├── %xsdPattern: "russian-doll"
└── %_diagnostics           ← enrichment diagnostics (if any issues)
    └── %_status: "complete"
```

IDE recommendations:
- **Visual distinction** — USDL nodes in blue (matching UTL-X brand color), raw nodes in default color
- **Toggle** — "Show: Raw / USDL / Both" button in the tree header
- **Collapsed by default** — `%types` section collapsed, expandable on demand
- **Autocompletion** — when typing `$input.`, suggest BOTH raw and USDL paths, visually distinguished
- **Diagnostics panel** — if ⚠, show the warnings inline in the tree or in a separate diagnostics tab

### Nested Schemas (xs:include / xs:import)

Nested schemas add complexity to enrichment:

**`xs:include` (same namespace):**
The included schema's types should be merged into the parent's `%types`. If the included file is available, enrichment resolves the types. If not available (file path not found), enrichment records a diagnostic warning and preserves the `xs:include` reference as-is.

**`xs:import` (different namespace):**
Imported types belong to a different namespace. In USDL, they could appear as:
- Separate entries in `%types` with a namespace prefix: `%types: {"ext:Address": {...}}`
- Or under a `%imports` directive: `%imports: {"urn:external": {types: {...}}}`

If the imported schema file is not available, the type references remain as strings (e.g., `%type: "ext:AddressType"`) with a diagnostic warning.

**Chameleon includes** (no target namespace — adopts parent's namespace):
These should be resolved and merged into the parent's `%types` transparently. The user shouldn't see them as separate — they become part of the parent schema's types.

### Functions for USDL Diagnostics

```utlx
// Check if enrichment succeeded:
$input["%_diagnostics"]["%_status"]              // "complete" or "partial"

// Get warnings:
$input["%_diagnostics"]["%_warnings"]            // array of warning objects

// Check if a specific feature is unsupported:
contains($input["%_diagnostics"]["%_unsupportedFeatures"] ?? [], "xs:any")

// Get enrichment coverage percentage:
$input["%_diagnostics"]["%_enrichmentCoverage"]  // "85%"
```

No special functions needed — the diagnostics are regular USDL `%` properties accessible with standard UTL-X expressions. The `%_` prefix (percent + underscore) distinguishes diagnostics from schema directives.

---

*Design document for USDL enrichment. May 2026.*
*This supersedes the original F08 "conversion" framing.*
*Existing USDL docs in docs/usdl/ cover directives and serializers — this doc covers the enrichment pipeline design.*

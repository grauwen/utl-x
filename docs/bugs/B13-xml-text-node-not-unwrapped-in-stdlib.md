# B13 — XML Text Nodes Not Unwrapped by Stdlib Conversion Functions

**Status:** Fixed  
**Severity:** High (361 call sites across 37 stdlib files potentially affected)  
**Affects:** stdlib (core), exposed by COMPILED and COPY strategies in UTLXe  
**Found:** 2026-04-24  
**Fixed:** 2026-04-24  
**Branch:** development  
**Fix applied:** `RuntimeOps.getProperty()` + `unwrapXmlTextNode()` in engine/strategy/compiled/RuntimeOps.kt  
**Stdlib changes needed:** None — the fix intercepts at the property access level, matching interpreter behavior

---

## Description

XML elements with simple text content like `<Amount>123.45</Amount>` are parsed by the XML parser into `UDM.Object(properties={_text=Scalar(value=123.45)})`. When stdlib functions like `toNumber()`, `parseNumber()`, or `toString()` receive this object, they fail because they only handle `UDM.Scalar`, not `UDM.Object` with a `_text` property.

## Root Cause

The UTL-X interpreter (`modules/core/interpreter`) auto-unwraps XML text nodes before passing values to stdlib functions. This masks the issue when using the TEMPLATE strategy (which goes through the interpreter).

The COMPILED and COPY strategies bypass the interpreter — they call stdlib functions directly via `StdlibDispatch.call()`. Without the interpreter's auto-unwrapping, XML text nodes arrive as `UDM.Object({_text: value})` and stdlib functions don't know how to handle them.

## Reproduction

```
%utlx 1.0
input xml
output json
---
{ amount: toNumber($input.Invoice.Amount) }
```

Input:
```xml
<Invoice><Amount>123.45</Amount></Invoice>
```

- **TEMPLATE strategy:** Works — interpreter unwraps `_text`, `toNumber` receives `"123.45"` as scalar
- **COMPILED strategy:** Fails — `toNumber` receives `UDM.Object({_text: "123.45"})`, returns null
- **COPY strategy:** Fails — same as COMPILED (auto-compiles internally)

## Affected Functions — Full Impact Analysis

**Total affected call sites: 361 across 37 stdlib files.**

The interpreter unwraps XML text nodes in `interpreter.kt` lines 567-580 via `unwrapTextNode()`. This function is ONLY called during property access evaluation, NOT during function argument processing. Every stdlib function that calls `.asString()`, `.asNumber()`, `.asBoolean()`, or checks `is UDM.Scalar` on its arguments is affected.

### CRITICAL — Core functions used in most transformations

| Category | Functions | Call sites | File |
|----------|-----------|-----------|------|
| **String** | `upperCase`, `lowerCase`, `toTitleCase`, `trim`, `substring`, `concat`, `split`, `join`, `replace`, `contains`, `startsWith`, `endsWith`, `length` | 19 | `string/StringFunctions.kt` |
| **String extended** | `substringBefore`, `substringAfter`, `substringBeforeLast`, `substringAfterLast`, `padStart`, `padEnd`, `ellipsis`, `repeat`, `wrap` | 20 | `string/ExtendedStringFunctions.kt` |
| **String more** | `translate`, `reverse`, `indexOfChar`, `lastIndexOfChar`, `charAtIndex`, `charCodeAt` | 15 | `string/MoreStringFunctions.kt` |
| **String regex** | `regexMatch`, `regexReplace`, `regexSplit`, `regexFind`, `regexTest` | 14 | `string/AdvancedRegexFunctions.kt` |
| **String chars** | `isAlpha`, `isNumeric`, `isAlphaNumeric`, `isUpperCase`, `isLowerCase`, `isBlank` | 11 | `string/CharacterFunctions.kt` |
| **Math** | `abs`, `round`, `ceil`, `floor`, `pow`, `sqrt` | 11 | `math/MathFunctions.kt` |
| **Math advanced** | `sin`, `cos`, `tan`, `log`, `log10`, `exp`, `asin`, `acos`, `atan`, `atan2` | 19 | `math/AdvancedMathFunctions.kt` |
| **Type conversion** | `toNumber`, `parseNumber`, `toString`, `toBoolean`, `typeOf`, `isString`, `isNumber`, `isBoolean` | 10 | `type/ConversionFunctions.kt` |
| **Date** | `parseDate`, `addDays`, `addHours`, `formatDate`, `diffDays` | 13 | `date/DateFunctions.kt` |
| **Date timezone** | `convertTimezone`, `toTimeZone`, `fromTimeZone`, `parseInTimeZone` | 11 | `date/TimezoneFunctions.kt` |

### HIGH — Business domain functions

| Category | Functions | Call sites | File |
|----------|-----------|-----------|------|
| **Finance** | `formatCurrency`, `parseCurrency`, `roundToPlaces`, `addTax`, `removeTax`, `getBaseAmount`, `applyDiscount`, `percentageChange`, `presentValue`, `futureValue`, `compoundInterest`, `simpleInterest` | 37 | `finance/FinancialFunctions.kt` |
| **Geospatial** | `distance`, `bearing`, `pointInPolygon`, `boundingBox`, coordinate processing | 34 | `geo/GeospatialFunctions.kt` |
| **XML encoding** | `detectedEncoding`, `convertEncoding`, `addBOM`, `stripBOM` | 14 | `xml/XMLEncodingBomFunctions.kt` |
| **XML CDATA** | CDATA processing functions | varies | `xml/CDATAFunctions.kt` |
| **XML QName** | QName functions | varies | `xml/QNameFunctions.kt` |
| **Serialization** | `toJson`, `fromJson`, `toYaml`, `fromYaml` | 20 | `serialization/SerializationFunctions.kt` |

### MEDIUM — Format and locale functions

| Category | Functions | Call sites | File |
|----------|-----------|-----------|------|
| **YAML** | YAML parsing and generation | 16 | `yaml/YAMLFunctions.kt` |
| **CSV** | CSV parsing and generation | 12 | `csv/CSVFunctions.kt` |
| **Regional** | Regional number formatting | 12 | `regional/RegionalNumberFunctions.kt` |
| **Logical** | Boolean operations | 11 | `logical/LogicalFunctions.kt` |
| **Encoding** | Base64, hex, URL encoding | 8 | `encoding/EncodingFunctions.kt` |
| **Schema** | Schema operations | 8 | `schema/` |
| **JWT/JWS** | Token functions | 7 | `jwt/`, `jws/` |
| **Util** | Utility functions | 4 | `util/` |
| **Core** | Core control flow | 3 | `core/` |

### LOW — Already handle objects correctly

| Category | Functions | Call sites | File |
|----------|-----------|-----------|------|
| **Array** | Most array functions handle types correctly | 9 | `array/MoreArrayFunctions.kt` |
| **Object** | Designed to work with objects | 2 | `objects/ObjectFunctions.kt` |
| **Binary** | Binary operations | 4 | `binary/` |

### Data flow comparison

```
INTERPRETER PATH (WORKS):
  XML parsing → UDM.Object({_text: value})
  Property access → interpreter calls unwrapTextNode()
  Function receives → UDM.Scalar(value)    ✓ stdlib handles this

COMPILED/COPY PATH (WAS BROKEN, NOW FIXED):
  XML parsing → UDM.Object({_text: value})
  Property access → RuntimeOps.getProperty() → unwrapXmlTextNode()  ← FIX APPLIED HERE
  Function receives → UDM.Scalar(value)    ✓ stdlib handles this
```

## Fix Applied: Option B — RuntimeOps.getProperty()

**File changed:** `modules/engine/src/main/kotlin/org/apache/utlx/engine/strategy/compiled/RuntimeOps.kt`

Added `unwrapXmlTextNode()` to `RuntimeOps.getProperty()` — the single chokepoint where compiled bytecode accesses object properties. Every `$input.foo.bar` in compiled code goes through this method. By unwrapping XML text nodes here, all downstream stdlib functions receive scalars — identical to the interpreter path.

```kotlin
@JvmStatic
fun getProperty(target: UDM, name: String): UDM {
    val value = when (target) {
        is UDM.Object -> target.properties[name] ?: UDM.Scalar.nullValue()
        else -> UDM.Scalar.nullValue()
    }
    return unwrapXmlTextNode(value)  // ← same behavior as interpreter
}

@JvmStatic
fun unwrapXmlTextNode(value: UDM): UDM {
    if (value is UDM.Object && value.properties.size == 1 && value.properties.containsKey("_text")) {
        return value.properties["_text"] ?: value
    }
    return value
}
```

**Zero stdlib files changed.** The fix intercepts at the property access level, matching the interpreter's `unwrapTextNode()` behavior.

### Remaining edge case

One scenario is NOT covered by this fix:

```
// COVERED: property access goes through getProperty() → unwrapped
toNumber($input.Invoice.Amount)

// NOT COVERED: $input IS the XML text node, no property access
toNumber($input)
```

The second case is essentially never done in practice — you don't pass an entire XML root element to `toNumber()`. If this edge case ever matters, Option A (below) would resolve it.

### Why Option A (UDM core) was not applied

Option A would add auto-unwrapping to `UDM.asString()`, `UDM.asNumber()`, `UDM.asBoolean()` in `modules/core/udm/udm_core.kt`:

```kotlin
fun asString(): String {
    val unwrapped = unwrapXmlTextNode()  // ← auto-unwrap before conversion
    return when (unwrapped) { ... }
}
```

| Aspect | Option B (applied) | Option A (deferred) |
|--------|-------------------|---------------------|
| **Scope** | Engine only (`RuntimeOps.kt`) | Core module (`udm_core.kt`) — shared by CLI, daemon, engine |
| **Risk** | Low — only affects compiled/copy path | Higher — changes core UDM behavior for all executables |
| **Stdlib changes** | None | None |
| **Covers `toNumber($input)` edge case** | No | Yes |
| **Double unwrapping** | No | Yes — interpreter unwraps, then `asString()` unwraps again (harmless but redundant) |
| **Future-proof** | New execution paths need their own unwrapping | All paths automatically covered |

**When to apply Option A:** If a new execution path is added beyond interpreter and compiled bytecode, or if the `toNumber($input)` edge case becomes a real issue. Until then, Option B is sufficient and contained.

## Impact (post-fix)

- **UTLXe COMPILED strategy:** Fixed — XML text nodes unwrapped at property access
- **UTLXe COPY strategy:** Fixed — uses compiled path internally
- **UTLXe TEMPLATE strategy:** Was never affected (interpreter handles unwrapping)
- **CLI (utlx):** Not affected (uses interpreter)
- **Daemon (utlxd):** Not affected (uses interpreter)

## Workaround

In `.utlx` transformations, wrap values with `toString()` before `toNumber()`:

```
// Instead of:
let amount = toNumber($input.Invoice.Amount)

// Use:
let amount = toNumber(toString($input.Invoice.Amount))
```

Note: This workaround may also fail if `toString()` itself doesn't unwrap XML text nodes. The proper fix is in the stdlib.

## Test References

- Conformance suite: `throughput/15_batch_xml_compiled_100.yaml` (skipped)
- Conformance suite: `throughput/16_batch_xml_copy_100.yaml` (skipped)
- Strategy parity gap: XML transforms produce different results across TEMPLATE vs COMPILED/COPY

## Priority

**High** — 361 call sites across 37 files. Affects ALL XML processing through COMPILED and COPY strategies (the production-optimized paths). TEMPLATE masks the bug via interpreter unwrapping. The recommended fix (Option A: UDM core) is a single-file change that resolves all 361 sites. Requires running all test suites: 467 CLI conformance + 207 engine Kotlin + 47 engine conformance.

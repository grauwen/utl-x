# B13 — XML Text Nodes Not Unwrapped by Stdlib Conversion Functions

**Status:** Open  
**Severity:** Medium  
**Affects:** stdlib (core), exposed by COMPILED and COPY strategies in UTLXe  
**Found:** 2026-04-24  
**Branch:** development

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

## Affected Functions

Any stdlib function that operates on scalar values from XML input:

- `toNumber()` / `parseNumber()` — `stdlib/type/ConversionFunctions.kt`
- `toString()` — `stdlib/type/ConversionFunctions.kt`
- `toBoolean()` — `stdlib/type/ConversionFunctions.kt`
- Potentially string functions (`upperCase`, `lowerCase`, `concat`, etc.) when receiving XML text nodes directly

## Fix

Update stdlib conversion functions to extract `_text` from `UDM.Object` before processing:

```kotlin
// In parseNumber and similar functions, add handling for XML text nodes:
fun parseNumber(args: List<UDM>): UDM {
    val value = args[0]
    
    // Unwrap XML text nodes: <Element>value</Element> → UDM.Object({_text: value})
    val unwrapped = when {
        value is UDM.Object && value.properties.containsKey("_text") -> 
            value.properties["_text"] ?: value
        else -> value
    }
    
    return when (unwrapped) {
        is UDM.Scalar -> // ... existing logic
        else -> defaultValue ?: UDM.Scalar(null)
    }
}
```

This fix should be applied to all affected functions in `ConversionFunctions.kt` and potentially as a shared utility `fun unwrapXmlTextNode(udm: UDM): UDM` in the stdlib.

## Impact

- **UTLXe COMPILED strategy:** Cannot process XML input that requires numeric/string conversion
- **UTLXe COPY strategy:** Same (uses COMPILED internally)
- **UTLXe TEMPLATE strategy:** Not affected (interpreter handles unwrapping)
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

Medium — affects XML processing in COMPILED/COPY strategies only. TEMPLATE works correctly. The fix is straightforward (stdlib change) but needs careful testing across all 467 CLI conformance tests to ensure no regressions.

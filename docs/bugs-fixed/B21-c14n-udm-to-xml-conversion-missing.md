# B21: c14n Functions Silently Return Null — UDM-to-XML Conversion Missing

**Status:** Open  
**Severity:** High (all c14n/canonicalization functions are non-functional)  
**Scope:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/XMLCanonicalizationFunctions.kt`  
**Affects:** JVM and native binary — not a GraalVM issue  
**Created:** May 2026  
**Discovered during:** v1.2.0 native binary validation

---

## Summary

All XML canonicalization functions (`c14n`, `c14nWithComments`, `excC14n`, `c14n11`, `c14nSubset`, `c14nHash`, `c14nEquals`, `canonicalizeWithAlgorithm`, etc.) silently return `null` on all inputs. The functions appear registered and callable, but produce no output.

## Root cause

The internal `parseXML(xml: UDM)` helper at line 814 of `XMLCanonicalizationFunctions.kt` receives a `UDM.Object` (because the XML parser already parsed the input into UDM), but it cannot convert it back to a raw XML string for the DOM parser:

```kotlin
private fun parseXML(xml: UDM): Document? {
    return try {
        val xmlString = when (xml) {
            is UDM.Scalar -> xml.value?.toString() ?: ""
            is UDM.Object -> {
                // Convert UDM Object to XML string
                // This would require XML serialization logic
                return null   // ← ALWAYS RETURNS NULL FOR XML INPUT
            }
            else -> return null
        }
        // ... DOM parsing never reached for UDM.Object
    } catch (e: Exception) {
        null
    }
}
```

The XML input arrives as `UDM.Object` (already parsed by the XML parser in the pipeline). The `parseXML` helper needs raw XML string to feed to `DocumentBuilderFactory`, but the UDM.Object → XML string conversion was never implemented. The TODO comment says "This would require XML serialization logic" — and it was left as `return null`.

All callers catch exceptions and return `UDM.Scalar(null)`, so the failure is completely silent.

## Impact

Every c14n function is non-functional:

| Function | Status | Since |
|---|---|---|
| `c14n()` | Returns null | Always |
| `c14nWithComments()` | Returns null | Always |
| `excC14n()` | Returns null | Always |
| `excC14nWithComments()` | Returns null | Always |
| `c14n11()` | Returns null | Always |
| `c14n11WithComments()` | Returns null | Always |
| `c14nPhysical()` | Returns null | Always |
| `c14nSubset()` | Returns null | Always |
| `c14nHash()` | Returns null | Always |
| `c14nEquals()` | Returns null | Always |
| `c14nFingerprint()` | Returns null | Always |
| `canonicalizeWithAlgorithm()` | Returns null | Always |

This has been broken since the functions were first added. It was not caught because:
1. The conformance suite doesn't test c14n functions
2. The `catch (e: Exception) { null }` pattern suppresses all errors
3. The native binary validation in v1.2.0 was the first time someone tested these end-to-end

## Fix

The `parseXML` helper needs to serialize `UDM.Object` back to XML string using `XMLSerializer` before passing to `DocumentBuilderFactory`:

```kotlin
is UDM.Object -> {
    XMLSerializer(prettyPrint = false, includeDeclaration = true).serialize(xml)
}
```

This is a one-line fix. The `XMLSerializer` already exists in `formats/xml` and is available as a dependency.

## Additional issues found in the same code

1. **Silent error swallowing** — all c14n functions catch `Exception` and return `null`. Should at minimum log the error, or propagate it so the user knows what went wrong.

2. **Error in test 5 of native binary validation** — `c14n` returning null was initially attributed to GraalVM reflection issues (B17). It was actually this bug all along.

## Files to modify

| File | Change |
|---|---|
| `stdlib/.../xml/XMLCanonicalizationFunctions.kt` line 819 | Add `XMLSerializer(prettyPrint = false).serialize(xml)` for `UDM.Object` case |
| `stdlib/build.gradle.kts` | May need dependency on `formats:xml` if not already present |

## Verification

```bash
# After fix — JVM:
echo '<doc><b/><a/></doc>' | ./utlx --from xml -e 'c14n($input)'
# Expected: "<doc><a></a><b></b></doc>" (canonical form — elements sorted)

# After fix — native binary (requires rebuild):
echo '<doc><b/><a/></doc>' | /tmp/utlx-release/utlx-macos-arm64 --from xml -e 'c14n($input)'
# Expected: same
```

---

*Bug B21. May 2026. Discovered during v1.2.0 native binary validation. All c14n functions have been non-functional since implementation — silent null return due to missing UDM-to-XML serialization in parseXML helper.*

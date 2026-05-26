# B22: isCanonicalJSON() Always Returns False

**Status:** Open  
**Severity:** Low (validation helper, not a core function)  
**Scope:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/json/JSONCanonicalizationFunctions.kt`  
**Affects:** JVM and native binary  
**Created:** May 2026  
**Discovered during:** v1.2.0 release validation

---

## Summary

`isCanonicalJSON()` always returns `false`, even for input that is already in canonical form per RFC 8785. The actual canonicalization functions (`canonicalizeJSON`, `jcs`, `canonicalJSONHash`) work correctly.

## Root cause

Line 403 of `JSONCanonicalizationFunctions.kt`:

```kotlin
val canonicalJSON = args[0].asString()
```

The input arrives as `UDM.Object` (the JSON parser already parsed it). `UDM.asString()` on an Object produces the Kotlin `toString()` representation (e.g., `Object(properties={a=Scalar(1)})`) — not the original JSON string. The function then checks this non-JSON string for canonical JSON properties (no whitespace, sorted keys) — which always fails.

## Why it's solvable without preserving the original input

The function doesn't need the original JSON string. It can:
1. Serialize the UDM to compact JSON using `JSONSerializer(prettyPrint = false)`
2. Canonicalize the UDM using the existing `canonicalizeJSON` logic
3. Compare the two strings — if they match, the input is canonical

```kotlin
fun isCanonicalJSON(args: List<UDM>): UDM {
    requireArgs(args, 1, "isCanonicalJSON")
    return try {
        val compact = JSONSerializer(prettyPrint = false).serialize(args[0])
        val canonical = canonicalize(args[0])  // existing internal method
        UDM.Scalar(compact == canonical)
    } catch (e: Exception) {
        UDM.Scalar(false)
    }
}
```

This works because:
- If the original JSON had sorted keys and no whitespace, `JSONSerializer` (which preserves UDM property order) produces the same output as the canonicalizer
- If keys were unsorted or whitespace was present, the JSON parser's UDM may or may not preserve the original order — but the comparison still tells you whether the *current structure* is canonical

**Caveat:** This checks whether the UDM *structure* is canonical, not whether the original byte string was canonical. For example, `{"a": 1}` (with space after colon) and `{"a":1}` (no space) both parse to the same UDM — so both would return the same result. The function cannot distinguish them without the original string.

For practical purposes this is fine — the use case is "check if this data is in canonical form", not "check if the wire bytes were canonical."

## Impact

Low. `isCanonicalJSON` is a validation helper. The actual canonicalization (`canonicalizeJSON`, `jcs`, `canonicalJSONHash`) all work correctly. No customer has reported this.

## Files to modify

| File | Change |
|---|---|
| `stdlib/.../json/JSONCanonicalizationFunctions.kt` line 401-430 | Rewrite to compare serialized vs canonical form |

---

*Bug B22. May 2026. Discovered during v1.2.0 release validation. Low severity — validation helper only.*

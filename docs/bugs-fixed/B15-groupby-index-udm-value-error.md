# B15: groupBy Result Cannot Be Indexed by UDM Value

**Status:** Open  
**Severity:** High  
**Discovered:** April 2026  
**Related:** F03 (join function proposal)

---

## Summary

The `groupBy()` function creates a map keyed by the lambda result, but indexing the result with a UDM value fails with "Index must be a number or string, got UDMValue". This forces users into O(N×M) filter loops instead of the efficient O(N+M) groupBy + lookup pattern.

## Reproduction

```utlx
let groups = groupBy($input.orderLines, (l) -> l.orderId)
groups[$input.orders[0].orderId]
```

**Error:**
```
Error: Index must be a number or string, got UDMValue
```

**Expected:** Returns the array of order lines matching the first order's ID.

## Root Cause

The index operator (`obj[key]`) in the interpreter checks the type of the key. When the key comes from a UDM property access (e.g., `order.orderId`), it's wrapped in a `RuntimeValue.UDMValue` containing a `UDM.Scalar`. The index operator expects a native `RuntimeValue.StringValue` or `RuntimeValue.NumberValue`, not a wrapped UDM value.

The fix: unwrap UDM scalars to native RuntimeValue types before indexing, or handle `UDMValue` in the index operator.

## Impact

- **The efficient O(N+M) flat-to-hierarchical pattern is broken.** Users who follow the documentation (groupBy + index lookup) hit this error.
- **Users fall back to O(N×M) filter loops.** For 500 parents × 5,000 children = 2.5 million comparisons instead of 5,500.
- **Performance difference:** 200-500ms (filter) vs 5-15ms (groupBy+index). 40x slower.
- **F03 (join function) would bypass this bug** but F03 is not yet implemented. The bug should be fixed regardless.

## Fix

In the interpreter's index access handling, unwrap UDM scalar values before comparison:

```kotlin
// In interpreter.kt, evaluateIndexAccess():
is RuntimeValue.UDMValue -> {
    // Unwrap UDM.Scalar to native RuntimeValue for index lookup
    when (val udm = indexValue.udm) {
        is UDM.Scalar -> when (udm.value) {
            is String -> RuntimeValue.StringValue(udm.value)
            is Number -> RuntimeValue.NumberValue(udm.value.toDouble())
            else -> indexValue // leave as-is for non-scalar
        }
        else -> indexValue
    }
}
```

## Effort

- Fix: ~10 lines in interpreter.kt
- Test: 2-3 conformance tests (groupBy + index with string key, number key, nested access)
- Total: 0.5 day

---

*Bug B15. April 2026. Blocks the efficient flat-to-hierarchical transformation pattern.*

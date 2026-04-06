# BUG: `truncate` causes silent crash (exit 1, no error output)

## Summary

Calling `truncate(string, maxLength)` causes the transformation to fail
silently: exit code 1, zero bytes on both stdout and stderr. No error
message is produced, making the failure impossible to diagnose without
binary search over the source.

---

## Reproduction

### Input (excerpt from `03-invoice.json`)

```json
{
  "lineItems": [
    {
      "description": "Cloud Infrastructure Services - Premium Tier",
      "amount": 15000.00
    }
  ]
}
```

### UTL-X transformation (minimal reproducer)

```utlx
%utlx 1.0
input json
output json
---

{
  result: truncate($input.lineItems[0].description, 50)
}
```

### Expected behaviour

```json
{
  "result": "Cloud Infrastructure Services - Premium Tier"
}
```

The string is 46 characters, shorter than the limit of 50.
`truncate` should return it unchanged.

### Actual behaviour

```
$ ./utlx transform test.utlx input.json
$ echo $?
1
```

Zero output. Zero error. Exit code 1.

---

## Confirmed workaround

Replacing `truncate(str, n)` with the raw `str` makes the
transformation succeed immediately:

```utlx
// FAILS silently:
Description: truncate(item.description, 50)

// WORKS:
Description: item.description
```

---

## Affected examples

All three examples that used `truncate` exhibited this silent crash:

| File | Call | String length vs limit |
|------|------|----------------------|
| `03-invoice-to-payment-instruction.utlx` | `truncate(item.description, 50)` | 46 < 50 |
| `05-customer-support-ticket-to-sla-compliance-report.utlx` | `truncate($input.subject, 60)` and `truncate(a.note, 80)` | both shorter than limit |
| `08-product-catalog-to-marketplace-listing-feed.utlx` | `truncate(product.description, 155)` | shorter than limit |

In all cases the input string was **shorter** than the truncation limit,
so `truncate` should have been a no-op.

---

## Investigation: source code review

Inspecting the `truncate` implementation in
`CaseConversionFunctions.kt:97-148` reveals that **the code looks safe**:

```kotlin
fun truncate(args: List<UDM>): UDM {
    // ... argument validation ...
    val input = str.value as String
    val max = (maxLength.value as Number).toInt()

    if (input.length <= max) {
        return UDM.Scalar(input)   // <-- should return early for our case
    }

    val truncateAt = max - ellipsis.length
    val result = input.take(truncateAt).trimEnd() + ellipsis  // uses .take(), safe
    return UDM.Scalar(result)
}
```

It uses Kotlin's `.take()` (which clamps internally) and has an early
return when `input.length <= max`. So the bounds-clamping hypothesis
from the earlier `substring` bug does **not** apply here.

### Likely root cause: function loading / registration failure

Since the implementation itself is correct, the silent crash most likely
comes from `truncate` not being found or not being callable through the
interpreter's function resolution path (`tryLoadStdlibFunction`). The
function lives in `CaseConversionFunctions.kt`, separate from the main
`StringFunctions.kt`. If it is not registered in the function registry
or not reachable via `tryDirectFunctionInvocation`, the interpreter
would:

1. Fail in `tryDirectFunctionInvocation` (throws `RuntimeError`)
2. Fall through to the registry lookup
3. Not find `truncate` in the registry
4. Fall through to `throw RuntimeError("Undefined function: truncate")`

The completely silent output (vs `substring` which at least prints
DEBUG lines) suggests that the function is **never found at all** --
neither the direct invocation path nor the registry path reaches it.

---

## Broader investigation: other string/array functions

A review of all index-based functions in the stdlib found:

### Vulnerable

| Function | File | Issue |
|----------|------|-------|
| `substring` | `StringFunctions.kt:135` | No bounds clamping -- passes `start`/`end` directly to Java `String.substring()` |

### Safe (proper bounds validation)

| Function | File | Validation method |
|----------|------|-------------------|
| `charAt` | `MoreStringFunctions.kt:201` | Explicit `if (index < 0 \|\| index >= str.length)` check |
| `charCodeAt` | `MoreStringFunctions.kt:231` | Explicit bounds check |
| `substringBefore` | `ExtendedStringFunctions.kt:31` | Uses `indexOf` before `substring` |
| `substringAfter` | `ExtendedStringFunctions.kt:61` | Uses `indexOf` before `substring` |
| `substringBeforeLast` | `ExtendedStringFunctions.kt:90` | Uses `lastIndexOf` before `substring` |
| `substringAfterLast` | `ExtendedStringFunctions.kt:119` | Uses `lastIndexOf` before `substring` |
| `extractBetween` | `ExtendedStringFunctions.kt:274` | Uses `indexOf` for both delimiters |
| `padLeft` / `padRight` | `ExtendedStringFunctions.kt` | Uses safe Kotlin `.padStart()`/`.padEnd()` |
| `get` (array) | `ArrayFunctions.kt:638` | Bounds check, returns null if out of range |
| `slice` (array) | `MoreArrayFunctions.kt:227` | Validates both start and end |
| `remove` (array) | `MoreArrayFunctions.kt:32` | Explicit bounds check |
| `insertBefore/After` | `MoreArrayFunctions.kt` | Explicit bounds checks |

### Conclusion

- **`substring`** is the only function with a direct bounds-clamping vulnerability.
- **`truncate`** has a different root cause: likely a function registration/loading
  issue rather than a bounds problem. The implementation is correct but the function
  appears unreachable at runtime.
- All other index-based string and array functions have proper validation.

---

## Suggested fixes

1. **`truncate` registration**: verify that `truncate` from `CaseConversionFunctions.kt`
   is properly registered in the function registry and reachable through
   `tryDirectFunctionInvocation`.
2. **`substring` bounds clamping** (`StringFunctions.kt:135`): clamp indices to
   `[0, str.length]` as every scripting language does.
3. **`tryLoadStdlibFunction` error handling** (`interpreter.kt:1008-1032`): rethrow
   all exceptions from stdlib functions as `RuntimeError` with the real message,
   instead of swallowing them and reporting "Undefined function".


Found the bug! Look at line 68-69 of CaseConversionFunctions.kt:

  @UTLXFunction(
      description = "Truncate string with ellipsis",
      minArgs = 3,   // <-- BUG: says 3
      maxArgs = 3,   // <-- BUG: says 3

  But the implementation at lines 97-99 expects 2-3 arguments:

  fun truncate(args: List<UDM>): UDM {
      if (args.size !in 2..3) {  // <-- accepts 2 OR 3 args
          throw IllegalArgumentException("truncate expects 2-3 arguments, got ${args.size}")

  Root cause: The @UTLXFunction annotation incorrectly requires exactly 3 arguments (minArgs = 3, maxArgs = 3), but the function implementation accepts 2-3 arguments (the ellipsis is optional, defaulting to "...").

  When you call truncate(str, 50) with 2 arguments, the function registry rejects it because the annotation says 3 are required. This causes a silent failure.

  Let me verify this is the only issue by checking function registration:

Summary of B04 fix and tests added:

  1. Fixed: minArgs = 3 → minArgs = 2 in CaseConversionFunctions.kt:69
  2. Added tests:
    - testTruncateB04RegressionTwoArguments() - Tests the exact B04 scenario:
        - truncate("Cloud Infrastructure Services - Premium Tier", 50) with 2 args
      - String shorter than limit returns unchanged
      - Truncation with 2 args uses default ellipsis "..."
    - testTruncateThreeArguments() - Verifies 3-arg calls still work:
        - Standard ellipsis truncation
      - Custom single-char ellipsis
      - String shorter than limit with custom ellipsis

  All tests pass. 

# BUG: `truncate` causes silent crash (exit 1, no error output)

>> string function with bounds issues 

## Summary

Calling `truncate(string, maxLength)` causes the transformation to fail
silently: exit code 1, zero bytes on both stdout and stderr. No error
message is produced, making the failure impossible to diagnose without
binary search over the source.

This is related to the `substring` bounds bug
(`BUG-substring-out-of-bounds-masked-as-undefined-function.md`) but
behaves worse -- `substring` at least prints a DEBUG line to stderr
before the misleading "Undefined function" error, while `truncate`
produces **nothing at all**.

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

## Likely root cause

`truncate` probably delegates to `substring` internally. The `substring`
implementation (`StringFunctions.kt:135`) passes indices directly to
Java's `String.substring(start, end)` without clamping:

```kotlin
return UDM.Scalar(str.substring(start, end))
```

When `end > str.length`, Java throws `StringIndexOutOfBoundsException`.

The exception is then swallowed by `Interpreter.tryLoadStdlibFunction`
(see `BUG-substring-out-of-bounds-masked-as-undefined-function.md` for
the full analysis of the error-masking catch block). For `truncate` the
masking is total -- no output reaches stderr at all, unlike `substring`
which at least prints a DEBUG line.

---

## Suggested fix

1. **`truncate` implementation**: clamp the end index to `min(maxLength, str.length)` before calling `substring`.
2. **`substring` implementation**: clamp both `start` and `end` to `[0, str.length]` as every other scripting language does.
3. **`tryLoadStdlibFunction` error handler**: rethrow all exceptions from stdlib functions as `RuntimeError` with the real message, instead of swallowing them silently.

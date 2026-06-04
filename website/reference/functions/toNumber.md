---
title: toNumber
description: "toNumber — UTL-X Type function. Convert a value to a number. Throws an error if the value cannot be"
pageClass: stdlib-page
---

# toNumber

<p class="stdlib-meta"><code>toNumber(value) → number</code> · <a href="/reference/stdlib#type">Type</a></p>

Convert a value to a number. Throws an error if the value cannot be
parsed.

- `value` (required): string, boolean, or number to convert

``` utlx
toNumber("42")                           // 42
toNumber("3.14")                         // 3.14
toNumber(true)                           // 1
toNumber(false)                          // 0
toNumber("not-a-number")                 // ERROR — runtime error

// Use case: XML values are always strings — convert for arithmetic
let quantity = toNumber($input.Order.Quantity)
let price = toNumber($input.Order.Price)
{lineTotal: quantity * price}
```

**Anti-pattern:** `toNumber()` on unvalidated user input without error
handling:

``` utlx
// BAD — crashes on invalid input:
toNumber($input.userProvidedValue)

// GOOD — safe with fallback:
try { toNumber($input.userProvidedValue) } catch { 0 }
```

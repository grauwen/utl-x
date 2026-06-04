---
title: xor
description: "xor — UTL-X Type function. Logical XOR (exclusive OR) — returns true when exactly one operand is"
pageClass: stdlib-page
---

# xor

<p class="stdlib-meta"><code>xor(a, b) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Logical XOR (exclusive OR) — returns true when exactly one operand is
true.

- `a` (required): first boolean

- `b` (required): second boolean

``` utlx
xor(true, false)                         // true
xor(true, true)                          // false
xor(false, false)                        // false
```

---
title: xnor
description: "xnor — UTL-X Type function. Logical XNOR (exclusive NOR / equivalence) — returns true when both"
pageClass: stdlib-page
---

# xnor

<p class="stdlib-meta"><code>xnor(a, b) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Logical XNOR (exclusive NOR / equivalence) — returns true when both
operands are the same.

- `a` (required): first boolean

- `b` (required): second boolean

``` utlx
xnor(true, true)                         // true
xnor(false, false)                       // true
xnor(true, false)                        // false
```

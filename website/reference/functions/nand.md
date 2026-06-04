---
title: nand
description: "nand — UTL-X Type function. Logical NAND (NOT AND). Returns true unless both arguments are true."
pageClass: stdlib-page
---

# nand

<p class="stdlib-meta"><code>nand(a, b) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Logical NAND (NOT AND). Returns `true` unless both arguments are `true`.

- `a` (required): first boolean

- `b` (required): second boolean

``` utlx
{
  bothTrue: nand(true, true),                // false
  mixed: nand(true, false),                  // true
  bothFalse: nand(false, false)              // true
}
```

---
title: nor
description: "nor — UTL-X Type function. Logical NOR (NOT OR). Returns true only when both arguments are"
pageClass: stdlib-page
---

# nor

<p class="stdlib-meta"><code>nor(a, b) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Logical NOR (NOT OR). Returns `true` only when both arguments are
`false`.

- `a` (required): first boolean

- `b` (required): second boolean

``` utlx
{
  bothFalse: nor(false, false),              // true
  oneTrue: nor(true, false),                 // false
  otherTrue: nor(false, true)                // false
}
```

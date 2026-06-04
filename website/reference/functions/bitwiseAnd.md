---
title: bitwiseAnd
description: "bitwiseAnd — UTL-X Binary function. Perform bitwise AND on two binary values."
pageClass: stdlib-page
---

# bitwiseAnd

<p class="stdlib-meta"><code>bitwiseAnd(binary1, binary2) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Perform bitwise AND on two binary values.

- `binary1` (required): first operand

- `binary2` (required): second operand (same length)

``` utlx
let mask = fromHex("FF00FF00")
let data = fromHex("AABBCCDD")
{
  masked: toHex(bitwiseAnd(data, mask))
}
// Output: {"masked": "aa00cc00"}
```

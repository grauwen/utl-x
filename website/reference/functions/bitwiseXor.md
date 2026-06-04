---
title: bitwiseXor
description: "bitwiseXor — UTL-X Binary function. Perform bitwise XOR on two binary values."
pageClass: stdlib-page
---

# bitwiseXor

<p class="stdlib-meta"><code>bitwiseXor(binary1, binary2) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Perform bitwise XOR on two binary values.

- `binary1` (required): first operand

- `binary2` (required): second operand (same length)

``` utlx
{
  xored: toHex(bitwiseXor(fromHex("AAFF"), fromHex("55FF")))
}
// Output: {"xored": "ff00"}
```

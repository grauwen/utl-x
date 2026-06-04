---
title: bitwiseNot
description: "bitwiseNot — UTL-X Binary function. Perform bitwise NOT (inversion) on a binary value."
pageClass: stdlib-page
---

# bitwiseNot

<p class="stdlib-meta"><code>bitwiseNot(binary) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Perform bitwise NOT (inversion) on a binary value.

- `binary` (required): binary data to invert

``` utlx
{
  inverted: toHex(bitwiseNot(fromHex("FF00")))
}
// Output: {"inverted": "00ff"}
```

---
title: bitwiseOr
description: "bitwiseOr — UTL-X Binary function. Perform bitwise OR on two binary values."
pageClass: stdlib-page
---

# bitwiseOr

<p class="stdlib-meta"><code>bitwiseOr(binary1, binary2) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Perform bitwise OR on two binary values.

- `binary1` (required): first operand

- `binary2` (required): second operand (same length)

``` utlx
{
  combined: toHex(bitwiseOr(fromHex("AA00"), fromHex("0055")))
}
// Output: {"combined": "aa55"}
```

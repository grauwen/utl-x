---
title: fromHex
description: "fromHex — UTL-X String function. Create binary data from a hexadecimal string."
pageClass: stdlib-page
---

# fromHex

<p class="stdlib-meta"><code>fromHex(hexString) → binary</code> · <a href="/reference/stdlib#string">String</a></p>

Create binary data from a hexadecimal string.

- `hexString` (required): hex-encoded string

``` utlx
{
  data: fromHex($input.hexPayload),
  text: toString(fromHex("48656c6c6f"))
}
```

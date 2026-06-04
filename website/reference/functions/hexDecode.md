---
title: hexDecode
description: "hexDecode — UTL-X Binary function. Decode a hex-encoded string to binary data."
pageClass: stdlib-page
---

# hexDecode

<p class="stdlib-meta"><code>hexDecode(hex) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Decode a hex-encoded string to binary data.

- `hex` (required): hex string to decode

``` utlx
{
  data: hexDecode($input.hexPayload)
}
```

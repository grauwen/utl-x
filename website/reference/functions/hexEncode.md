---
title: hexEncode
description: "hexEncode — UTL-X Binary function. Encode binary data as a hexadecimal string."
pageClass: stdlib-page
---

# hexEncode

<p class="stdlib-meta"><code>hexEncode(data) → string</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Encode binary data as a hexadecimal string.

- `data` (required): binary data to encode

``` utlx
{
  hex: hexEncode(toBinary($input.text))
}
```

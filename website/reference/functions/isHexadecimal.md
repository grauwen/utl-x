---
title: isHexadecimal
description: "isHexadecimal — UTL-X String function. Returns true if the string contains only valid hexadecimal characters"
pageClass: stdlib-page
---

# isHexadecimal

<p class="stdlib-meta"><code>isHexadecimal(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains only valid hexadecimal characters
(0-9, A-F, a-f).

- `string` (required): string to test

``` utlx
isHexadecimal("1a2b3c")                  // true
isHexadecimal("xyz")                     // false
{
  validHex: isHexadecimal($input.colorCode)
}
```

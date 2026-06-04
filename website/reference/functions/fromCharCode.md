---
title: fromCharCode
description: "fromCharCode — UTL-X String function. Create a string from a Unicode character code (code point)."
pageClass: stdlib-page
---

# fromCharCode

<p class="stdlib-meta"><code>fromCharCode(code) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Create a string from a Unicode character code (code point).

- `code` (required): integer code point

``` utlx
fromCharCode(65)                         // "A"
fromCharCode(8364)                       // "€"
{
  char: fromCharCode($input.code)
}
```

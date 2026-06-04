---
title: isNumber
description: "isNumber — UTL-X Type function. Returns true if the value is a number (integer or decimal)."
pageClass: stdlib-page
---

# isNumber

<p class="stdlib-meta"><code>isNumber(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is a number (integer or decimal).

- `value` (required): the value to test

``` utlx
isNumber(42)                             // true
isNumber(3.14)                           // true
isNumber("42")                           // false (string, not number)
isNumber(true)                           // false
```

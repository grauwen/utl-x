---
title: isBoolean
description: "isBoolean — UTL-X Type function. Returns true if the value is a boolean (true or false)."
pageClass: stdlib-page
---

# isBoolean

<p class="stdlib-meta"><code>isBoolean(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is a boolean (`true` or `false`).

- `value` (required): the value to test

``` utlx
isBoolean(true)                          // true
isBoolean(false)                         // true
isBoolean("true")                        // false (string, not boolean)
isBoolean(1)                             // false (number, not boolean)
```

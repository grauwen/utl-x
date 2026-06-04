---
title: isString
description: "isString — UTL-X Type function. Returns true if the value is a string."
pageClass: stdlib-page
---

# isString

<p class="stdlib-meta"><code>isString(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is a string.

- `value` (required): the value to test

``` utlx
isString("hello")                        // true
isString(42)                             // false
isString(null)                           // false
```

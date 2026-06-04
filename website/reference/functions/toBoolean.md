---
title: toBoolean
description: "toBoolean — UTL-X Type function. Convert a value to boolean."
pageClass: stdlib-page
---

# toBoolean

<p class="stdlib-meta"><code>toBoolean(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Convert a value to boolean.

- `value` (required): string, number, or boolean to convert

``` utlx
toBoolean("true")                        // true
toBoolean("false")                       // false
toBoolean(1)                             // true
toBoolean(0)                             // false
toBoolean("yes")                         // true
toBoolean("no")                          // false
```

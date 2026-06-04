---
title: isEmpty
description: "isEmpty — UTL-X Type function. Returns true if the value is null, empty string, or empty array."
pageClass: stdlib-page
---

# isEmpty

<p class="stdlib-meta"><code>isEmpty(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is null, empty string, or empty array.

- `value` (required): the value to test

``` utlx
isEmpty(null)                            // true
isEmpty("")                              // true
isEmpty([])                              // true (empty array)
isEmpty("hello")                         // false
isEmpty([1])                             // false
```

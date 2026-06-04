---
title: isNotEmpty
description: "isNotEmpty — UTL-X Type function. Checks if a value is not empty (inverse of isEmpty). Returns true for"
pageClass: stdlib-page
---

# isNotEmpty

<p class="stdlib-meta"><code>isNotEmpty(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Checks if a value is not empty (inverse of isEmpty). Returns true for
non-null, non-empty values.

- `value` (required): value to test

``` utlx
isNotEmpty("hello")                      // true
isNotEmpty("")                           // false
isNotEmpty(null)                         // false
isNotEmpty([1, 2])                       // true
{
  hasName: isNotEmpty($input.name),
  hasItems: isNotEmpty($input.items)
}
```

---
title: isDefined
description: "isDefined — UTL-X Type function. Returns true if the value is not null. Empty strings and zero are"
pageClass: stdlib-page
---

# isDefined

<p class="stdlib-meta"><code>isDefined(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is not null. Empty strings and zero are
considered defined.

- `value` (required): the value to test

``` utlx
isDefined(null)                          // false
isDefined("")                            // true (empty string IS defined)
isDefined(0)                             // true (zero IS defined)
isDefined($input.name)                   // true if field exists and is not null
```

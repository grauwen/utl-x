---
title: isNull
description: "isNull — UTL-X Type function. Returns true if the value is null."
pageClass: stdlib-page
---

# isNull

<p class="stdlib-meta"><code>isNull(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is null.

- `value` (required): the value to test

``` utlx
isNull(null)                             // true
isNull("")                               // false (empty string is not null)
isNull(0)                                // false (zero is not null)
isNull($input.optionalField)             // true if field is missing or null
```

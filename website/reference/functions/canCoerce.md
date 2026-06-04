---
title: canCoerce
description: "canCoerce — UTL-X Type function. Check if a value can be coerced to a target type without error."
pageClass: stdlib-page
---

# canCoerce

<p class="stdlib-meta"><code>canCoerce(value, targetType) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Check if a value can be coerced to a target type without error.

- `value` (required): the value to test

- `targetType` (required): target type as string — `"number"`,
  `"boolean"`, `"date"`, etc.

``` utlx
canCoerce("123", "number")      // true
canCoerce("abc", "number")      // false
canCoerce("true", "boolean")    // true
```

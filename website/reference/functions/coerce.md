---
title: coerce
description: "coerce — UTL-X Type function. Coerce a value to a target type, returning a default if coercion fails."
pageClass: stdlib-page
---

# coerce

<p class="stdlib-meta"><code>coerce(value, targetType, default?) → value</code> · <a href="/reference/stdlib#type">Type</a></p>

Coerce a value to a target type, returning a default if coercion fails.

- `value` (required): value to coerce

- `targetType` (required): target type — `"number"`, `"boolean"`,
  `"string"`, `"date"`

- `default` (optional): fallback value on failure

``` utlx
coerce("123", "number", 0)       // 123
coerce("abc", "number", 0)       // 0 (fallback)
coerce("true", "boolean", false) // true
```

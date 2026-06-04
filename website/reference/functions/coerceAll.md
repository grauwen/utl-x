---
title: coerceAll
description: "coerceAll — UTL-X Type function. Coerce all values in an array to a target type."
pageClass: stdlib-page
---

# coerceAll

<p class="stdlib-meta"><code>coerceAll(array, targetType, default?) → array</code> · <a href="/reference/stdlib#type">Type</a></p>

Coerce all values in an array to a target type.

- `array` (required): array of values to coerce

- `targetType` (required): target type — `"number"`, `"boolean"`,
  `"string"`

- `default` (optional): fallback value for failed coercions

``` utlx
coerceAll(["1", "2", "three", "4"], "number", 0)
// [1, 2, 0, 4]
```

---
title: flattenDeep
description: "flattenDeep — UTL-X Array function. Remove ALL levels of array nesting, recursively. Produces a completely"
pageClass: stdlib-page
---

# flattenDeep

<p class="stdlib-meta"><code>flattenDeep(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Remove ALL levels of array nesting, recursively. Produces a completely
flat array.

- `array` (required): the deeply nested array to flatten

``` utlx
{
  deep: flattenDeep([[1, [2, [3, [4]]]]]),
  // [1, 2, 3, 4]
  nested: flattenDeep([[[["deep"]]]]),
  // ["deep"]
  flat: flattenDeep([1, 2, 3])
  // [1, 2, 3]  (already flat — no change)
}
```

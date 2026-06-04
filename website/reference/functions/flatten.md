---
title: flatten
description: "flatten — UTL-X Array function. Remove one level of array nesting. Each element that is an array is"
pageClass: stdlib-page
---

# flatten

<p class="stdlib-meta"><code>flatten(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Remove one level of array nesting. Each element that is an array is
unwrapped; non-array elements are kept as-is.

- `array` (required): the nested array to flatten

``` utlx
{
  flat: flatten([[1, 2], [3, 4], [5]]),
  // [1, 2, 3, 4, 5]
  mixed: flatten([[1, 2], 3, [4, 5]]),
  // [1, 2, 3, 4, 5]
  oneLevel: flatten([[[1, 2]], [[3]]])
  // [[1, 2], [3]]  (only ONE level removed)
}
```

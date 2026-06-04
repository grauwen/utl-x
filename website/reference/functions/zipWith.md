---
title: zipWith
description: "zipWith — UTL-X Array function. Combine two arrays element-by-element using a merge function."
pageClass: stdlib-page
---

# zipWith

<p class="stdlib-meta"><code>zipWith(arr1, arr2, fn) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Combine two arrays element-by-element using a merge function.

- `arr1` (required): first array

- `arr2` (required): second array

- `fn` (required): lambda `(elem1, elem2) -> combined`

``` utlx
zipWith([1, 2, 3], [10, 20, 30], (a, b) -> a + b)  // [11, 22, 33]
```

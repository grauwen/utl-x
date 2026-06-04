---
title: union
description: "union — UTL-X Array function. Combine two arrays, removing duplicates. Returns all unique values from"
pageClass: stdlib-page
---

# union

<p class="stdlib-meta"><code>union(arr1, arr2) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Combine two arrays, removing duplicates. Returns all unique values from
both.

- `arr1` (required): first array

- `arr2` (required): second array

``` utlx
union([1, 2, 3], [3, 4, 5])                // [1, 2, 3, 4, 5]
union(["A", "B"], ["B", "C", "D"])          // ["A", "B", "C", "D"]
```

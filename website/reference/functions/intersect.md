---
title: intersect
description: "intersect — UTL-X Array function. Return values present in BOTH arrays."
pageClass: stdlib-page
---

# intersect

<p class="stdlib-meta"><code>intersect(arr1, arr2) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Return values present in BOTH arrays.

- `arr1` (required): first array

- `arr2` (required): second array

``` utlx
intersect([1, 2, 3], [2, 3, 4])            // [2, 3]
intersect(["A", "B", "C"], ["X", "Y"])     // [] (no common elements)
```

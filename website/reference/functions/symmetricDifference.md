---
title: symmetricDifference
description: "symmetricDifference — UTL-X Array function. Return values that are in EITHER array but NOT in both. The 'exclusive"
pageClass: stdlib-page
---

# symmetricDifference

<p class="stdlib-meta"><code>symmetricDifference(arr1, arr2) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Return values that are in EITHER array but NOT in both. The "exclusive
or" of two arrays.

- `arr1` (required): first array

- `arr2` (required): second array

``` utlx
symmetricDifference([1, 2, 3], [2, 3, 4])  // [1, 4]
symmetricDifference([1, 2], [1, 2])         // [] (identical)
```

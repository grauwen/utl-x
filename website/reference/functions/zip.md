---
title: zip
description: "zip — UTL-X Array function. Combine two arrays element-by-element into pairs. Truncated to the"
pageClass: stdlib-page
---

# zip

<p class="stdlib-meta"><code>zip(arr1, arr2) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Combine two arrays element-by-element into pairs. Truncated to the
shorter array's length.

- `arr1` (required): first array

- `arr2` (required): second array

``` utlx
zip([1, 2, 3], ["a", "b", "c"])            // [[1, "a"], [2, "b"], [3, "c"]]
```

``` utlx
let headers = ["Name", "Age", "City"]
let row = ["Alice", "30", "Amsterdam"]
{
  record: fromEntries(zip(headers, row))
}
```

Also: `zipAll(arrays)` — zips any number of arrays, `unzip(pairs)` —
reverse of zip.

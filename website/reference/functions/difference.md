---
title: difference
description: "difference — UTL-X Array function. Return values in arr1 that are NOT in arr2. Order matters —"
pageClass: stdlib-page
---

# difference

<p class="stdlib-meta"><code>difference(arr1, arr2) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Return values in `arr1` that are NOT in `arr2`. Order matters —
`difference(a, b)` is not the same as `difference(b, a)`.

- `arr1` (required): the source array

- `arr2` (required): the array to subtract

``` utlx
difference([1, 2, 3], [2, 3, 4])           // [1]
difference([2, 3, 4], [1, 2, 3])           // [4]
```

``` utlx
let previous = map($input.previousOrders, (o) -> o.id)
let current = map($input.currentOrders, (o) -> o.id)
{
  newOrders: difference(current, previous),
  removedOrders: difference(previous, current)
}
```

---
title: transpose
description: "transpose — UTL-X Array function. Transpose a 2D array — rows become columns, columns become rows."
pageClass: stdlib-page
---

# transpose

<p class="stdlib-meta"><code>transpose(array2D) → array2D</code> · <a href="/reference/stdlib#array">Array</a></p>

Transpose a 2D array — rows become columns, columns become rows.

- `array2D` (required): array of arrays (all same length)

``` utlx
transpose([[1, 2, 3], [4, 5, 6]])          // [[1, 4], [2, 5], [3, 6]]
```

``` utlx
{
  pivoted: transpose($input.table)
}
```

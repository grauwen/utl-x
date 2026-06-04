---
title: crossJoin
description: "crossJoin — UTL-X Array function. Cartesian product of two arrays — every combination of elements from"
pageClass: stdlib-page
---

# crossJoin

<p class="stdlib-meta"><code>crossJoin(array1, array2) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Cartesian product of two arrays — every combination of elements from
both.

- `array1` (required): first array

- `array2` (required): second array

``` bash
echo '{"sizes": ["S", "M", "L"], "colors": ["red", "blue"]}' \
  | utlx -e 'crossJoin($input.sizes, $input.colors)'
# [["S","red"],["S","blue"],["M","red"],["M","blue"],["L","red"],["L","blue"]]
```

``` utlx
{
  variants: map(crossJoin($input.sizes, $input.colors), (pair) -> {
    size: pair[0], color: pair[1]
  })
}
```

---
title: flatMap
description: "flatMap — UTL-X Array function. Map each element to an array, then flatten one level. Equivalent to"
pageClass: stdlib-page
---

# flatMap

<p class="stdlib-meta"><code>flatMap(array, fn) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Map each element to an array, then flatten one level. Equivalent to
`flatten(map(...))`. Use when each element produces multiple results.

- `array` (required): the array to process

- `fn` (required): lambda `(element) -> array`

``` bash
echo '{"orders": [{"lines": [1,2]}, {"lines": [3]}]}' \
  | utlx -e 'flatMap(.orders, (o) -> o.lines)'
# [1, 2, 3]
```

``` utlx
{
  allLines: flatMap($input.orders, (o) -> o.lines)
}
```

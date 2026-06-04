---
title: all
description: "all — UTL-X Array function. Returns true if ALL elements satisfy the predicate. Returns true for"
pageClass: stdlib-page
---

# all

<p class="stdlib-meta"><code>all(array, predicate) → boolean</code> · <a href="/reference/stdlib#array">Array</a></p>

Returns true if ALL elements satisfy the predicate. Returns true for
empty arrays (vacuously true).

- `array` (required): the array to test

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '{"items": [{"price": 10}, {"price": 25}, {"price": 5}]}' \
  | utlx -e 'all($input.items, (item) -> item.price > 0)'
# true
```

``` utlx
{
  allPositive: all($input.items, (item) -> item.price > 0),
  allExpensive: all($input.items, (item) -> item.price > 20)
}
// Output: {"allPositive": true, "allExpensive": false}
```

---
title: none
description: "none — UTL-X Array function. Check if no elements in the array match the predicate (all return"
pageClass: stdlib-page
---

# none

<p class="stdlib-meta"><code>none(array, predicate) → boolean</code> · <a href="/reference/stdlib#array">Array</a></p>

Check if no elements in the array match the predicate (all return
`false`). Opposite of `some()`.

- `array` (required): array to test

- `predicate` (required): lambda `(element) -> boolean`

``` utlx
{
  allSmall: none([1, 2, 3], (x) -> x > 10),     // true (no element > 10)
  hasLarge: none([1, 2, 3], (x) -> x > 2)       // false (3 > 2)
}
```

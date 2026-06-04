---
title: map
description: "map — UTL-X Array function. Transform every element of an array. The most-used function in UTL-X."
pageClass: stdlib-page
---

# map

<p class="stdlib-meta"><code>map(array, fn) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Transform every element of an array. The most-used function in UTL-X.
Returns a new array of the same length.

- `array` (required): the array to transform

- `fn` (required): lambda `(element) -> newValue` or
  `(element, index) -> newValue`

``` bash
echo '[1, 2, 3]' | utlx -e 'map(., (x) -> x * 2)'
# [2, 4, 6]
```

``` utlx
{
  lines: map($input.items, (item) -> {
    product: item.name,
    priceWithTax: item.price * 1.21
  }),

  // With index (second parameter):
  numbered: map($input.items, (item, index) -> {
    lineNumber: index + 1,
    product: item.name
  })
}
```

**Anti-pattern:** using `map()` to filter —
`map(arr, (x) -> if (x.active) x else null)` produces nulls. Use
`filter()` to remove, then `map()` to transform.

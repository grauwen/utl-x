---
title: first
description: "first — UTL-X Array function. Returns the first element of an array, or null if the array is empty."
pageClass: stdlib-page
---

# first

<p class="stdlib-meta"><code>first(array) → element or null</code> · <a href="/reference/stdlib#array">Array</a></p>

Returns the first element of an array, or `null` if the array is empty.

- `array` (required): the source array

``` utlx
first(["Apple", "Banana", "Cherry"])     // "Apple"
first([42])                              // 42
first([])                                // null

// Use case: get the cheapest product
{
  cheapest: first(sortBy($input.products, (p) -> p.price))
}
```

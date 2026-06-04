---
title: sortBy
description: "sortBy — UTL-X Array function. Sort an array using a key extractor function."
pageClass: stdlib-page
---

# sortBy

<p class="stdlib-meta"><code>sortBy(array, keyFn) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Sort an array using a key extractor function.

- `array` (required): the array to sort

- `keyFn` (required): lambda `(element) -> sortKey`

``` bash
echo '{"products": [{"name": "Widget", "price": 25}, {"name": "Gadget", "price": 150}, {"name": "Gizmo", "price": 10}]}' | utlx -e 'sortBy($input.products, (p) -> p.price)'
# [{"name": "Gizmo", "price": 10}, {"name": "Widget", "price": 25}, {"name": "Gadget", "price": 150}]
```

``` utlx
{
  cheapestFirst: sortBy($input.products, (p) -> p.price),
  expensiveFirst: sortBy($input.products, (p) -> -p.price),
  alphabetical: sortBy($input.products, (p) -> p.name)
}
```

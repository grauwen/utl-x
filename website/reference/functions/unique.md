---
title: unique
description: "unique — UTL-X Array function. Remove duplicate values. Alias for distinct(). Preserves first"
pageClass: stdlib-page
---

# unique

<p class="stdlib-meta"><code>unique(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Remove duplicate values. Alias for `distinct()`. Preserves first
occurrence.

- `array` (required): the array to deduplicate

``` utlx
unique([1, 2, 2, 3, 3, 3])              // [1, 2, 3]
unique(["apple", "banana", "apple"])     // ["apple", "banana"]

// Use case: collect unique customer IDs
{
  customers: unique(map($input.orders, (o) -> o.customerId))
}
```

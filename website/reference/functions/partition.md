---
title: partition
description: "partition — UTL-X Array function. Split an array into two: elements that match the predicate, and elements"
pageClass: stdlib-page
---

# partition

<p class="stdlib-meta"><code>partition(array, predicate) → \[matching, nonMatching\]</code> · <a href="/reference/stdlib#array">Array</a></p>

Split an array into two: elements that match the predicate, and elements
that don't. Returns a 2-element array of arrays.

- `array` (required): the array to partition

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '{"orders": [{"id": 1, "amount": 500}, {"id": 2, "amount": 1500}, {"id": 3, "amount": 200}]}' | utlx -e 'partition($input.orders, (o) -> o.amount > 1000)'
# [[{"id": 2, "amount": 1500}], [{"id": 1, "amount": 500}, {"id": 3, "amount": 200}]]
```

``` utlx
let validated = partition($input.records, (r) -> r.email != null && r.name != null)
{
  valid: validated[0],
  invalid: validated[1],
  validCount: count(validated[0]),
  invalidCount: count(validated[1])
}
```

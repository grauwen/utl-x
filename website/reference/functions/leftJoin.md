---
title: leftJoin
description: "leftJoin — UTL-X Array function. Left join – returns all items from the left array, with matching items"
pageClass: stdlib-page
---

# leftJoin

<p class="stdlib-meta"><code>leftJoin(left, right, leftKeyFn, rightKeyFn) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Left join – returns all items from the left array, with matching items
from the right (or `null` for non-matches).

- `left` (required): left array (all items preserved)

- `right` (required): right array

- `leftKeyFn` (required): lambda extracting key from left items

- `rightKeyFn` (required): lambda extracting key from right items

``` utlx
let customers = [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
let orders = [{customerId: 1, product: "Widget"}]

{
  result: leftJoin(customers, orders, (c) -> c.id, (o) -> o.customerId)
  // [{l: {id: 1, name: "Alice"}, r: {customerId: 1, product: "Widget"}},
  //  {l: {id: 2, name: "Bob"}, r: null}]
}
```

---
title: rightJoin
description: "rightJoin — UTL-X Array function. Right join — returns all items from the right array, with matching items"
pageClass: stdlib-page
---

# rightJoin

<p class="stdlib-meta"><code>rightJoin(left, right, leftKey, rightKey) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Right join — returns all items from the right array, with matching items
from the left. Unmatched left fields are null.

- `left` (required): left array

- `right` (required): right array

- `leftKey` (required): lambda to extract join key from left elements

- `rightKey` (required): lambda to extract join key from right elements

``` utlx
let orders = [{id: 1, product: "A"}, {id: 2, product: "B"}]
let shipments = [{orderId: 2, date: "2026-05-01"}, {orderId: 3, date: "2026-05-02"}]
rightJoin(orders, shipments, (o) -> o.id, (s) -> s.orderId)
// includes shipment for orderId 3 even though no matching order
```

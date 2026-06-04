---
title: unnest
description: "unnest — UTL-X Array function. Flatten nested children alongside parent fields. The reverse of nestBy"
pageClass: stdlib-page
---

# unnest

<p class="stdlib-meta"><code>unnest(array, childKey) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Flatten nested children alongside parent fields. The reverse of `nestBy`
— converts hierarchical data to flat rows. See Chapter 20.

- `array` (required): array of parent objects containing nested children

- `childKey` (required): the key holding the children array

``` bash
echo '{"orders": [{"customer": "Alice", "items": [{"sku": "A1", "qty": 2}, {"sku": "B2", "qty": 1}]}, {"customer": "Bob", "items": [{"sku": "C3", "qty": 5}]}]}' | utlx -e 'unnest($input.orders, "items")'
# [{"customer":"Alice","sku":"A1","qty":2},{"customer":"Alice","sku":"B2","qty":1},{"customer":"Bob","sku":"C3","qty":5}]
```

``` utlx
// Input: [{customer: "Alice",
//          items: [{sku: "A1", qty: 2}, {sku: "B2", qty: 1}]}]
// Output: [{customer: "Alice", sku: "A1", qty: 2},
//          {customer: "Alice", sku: "B2", qty: 1}]
// Typical use: denormalize order/line-items for flat CSV export
let flat = unnest($input.orders, "items")
map(flat, (row) -> {
  customer: row.customer,
  sku: row.sku,
  quantity: row.qty
})
```

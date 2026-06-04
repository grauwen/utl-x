---
title: joinWith
description: "joinWith — UTL-X Array function. Inner join two arrays by key with a custom combiner function. Unlike"
pageClass: stdlib-page
---

# joinWith

<p class="stdlib-meta"><code>joinWith(left, right, leftKeyFn, rightKeyFn, combinerFn) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Inner join two arrays by key with a custom combiner function. Unlike
`join()` which returns `{l, r}` pairs, `joinWith` lets you shape the
output.

- `left` (required): left array

- `right` (required): right array

- `leftKeyFn` (required): lambda extracting key from left items

- `rightKeyFn` (required): lambda extracting key from right items

- `combinerFn` (required): lambda `(leftItem, rightItem) -> result`

``` utlx
let customers = [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
let orders = [{customerId: 1, product: "Widget"}]

{
  joined: joinWith(customers, orders,
    (c) -> c.id, (o) -> o.customerId,
    (c, o) -> {name: c.name, product: o.product}
  )
  // [{name: "Alice", product: "Widget"}]
}
```

## K

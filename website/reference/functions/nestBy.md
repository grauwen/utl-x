---
title: nestBy
description: "nestBy — UTL-X Array function. Nest children under parents by matching keys. Creates a 1:N parent-child"
pageClass: stdlib-page
---

# nestBy

<p class="stdlib-meta"><code>nestBy(parents, children, parentKeyFn, childKeyFn, childProperty) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Nest children under parents by matching keys. Creates a 1:N parent-child
hierarchy – the most common flat-to-hierarchical integration pattern.
Performance is O(N+M) using a hash index. See Chapter 20.

- `parents` (required): array of parent records

- `children` (required): array of child records

- `parentKeyFn` (required): lambda extracting the join key from each
  parent

- `childKeyFn` (required): lambda extracting the join key from each
  child

- `childProperty` (required): string name for the new property on each
  parent

``` utlx
// Input: flat orders and lines from two separate sources
let orders = $input.orders
// [{orderId: "O-1", customer: "Alice"}, {orderId: "O-2", customer: "Bob"}]
let lines = $input.lines
// [{orderId: "O-1", sku: "W-01"}, {orderId: "O-1", sku: "G-02"},
//  {orderId: "O-2", sku: "W-01"}]

{
  nested: nestBy(orders, lines, (o) -> o.orderId, (l) -> l.orderId, "lines")
  // Output:
  // [{orderId: "O-1", customer: "Alice",
  //   lines: [{orderId: "O-1", sku: "W-01"}, {orderId: "O-1", sku: "G-02"}]},
  //  {orderId: "O-2", customer: "Bob",
  //   lines: [{orderId: "O-2", sku: "W-01"}]}]
}
```

``` bash
echo '{"depts":[{"id":"D1","name":"Eng"}],"emps":[{"dept":"D1","name":"Alice"},{"dept":"D1","name":"Bob"}]}' | utlx -e 'nestBy($input.depts, $input.emps, (d) -> d.id, (e) -> e.dept, "members")'
# [{"id":"D1","name":"Eng","members":[{"dept":"D1","name":"Alice"},{"dept":"D1","name":"Bob"}]}]
```

**Inverse:** `unnest(array, "children")` flattens the hierarchy back to
a flat array.

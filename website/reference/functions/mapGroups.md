---
title: mapGroups
description: "mapGroups — UTL-X Array function. Group array elements by key and transform each group. Returns an array"
pageClass: stdlib-page
---

# mapGroups

<p class="stdlib-meta"><code>mapGroups(array, keySelector, transform) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Group array elements by key and transform each group. Returns an array
of transformed results – ideal for reporting and aggregation. See
Chapter 20.

- `array` (required): input array to group

- `keySelector` (required): lambda `(item) -> key` or string property
  name

- `transform` (required): lambda receiving `{key, value}` group object

``` utlx
// Input: flat array of orders with region field
let sales = $input.orders
// [{region: "EU", amount: 100}, {region: "US", amount: 200}, ...]

{
  summary: mapGroups(sales, "region", (group) -> {
    region: group.key,
    count: count(group.value),
    total: sum(map(group.value, (o) -> o.amount))
  })
  // Output: [{region: "EU", count: 3, total: 450},
  //          {region: "US", count: 2, total: 380}]
}
```

``` bash
echo '[{"dept":"Eng","name":"Alice"},{"dept":"Eng","name":"Bob"},{"dept":"Sales","name":"Carol"}]' | utlx -e 'mapGroups($input, "dept", (g) -> {dept: g.key, headcount: count(g.value)})'
# [{"dept":"Eng","headcount":2},{"dept":"Sales","headcount":1}]
```

**Difference from `groupBy`:** `groupBy` returns an Object (keyed map
for O(1) lookup); `mapGroups` returns an Array (for
iteration/reporting).

---
title: groupBy
description: "groupBy — UTL-X Array function. Group array elements by a computed key. Returns an object where keys are"
pageClass: stdlib-page
---

# groupBy

<p class="stdlib-meta"><code>groupBy(array, keyFn) → object</code> · <a href="/reference/stdlib#array">Array</a></p>

Group array elements by a computed key. Returns an object where keys are
the group values and values are arrays of matching elements.

- `array` (required): the array to group

- `keyFn` (required): lambda `(element) -> groupKey`

``` bash
echo '{"employees": [{"name": "Alice", "dept": "Eng"}, {"name": "Bob", "dept": "Sales"}]}' \
  | utlx -e 'groupBy($input.employees, (e) -> e.dept)'
# {"Eng": [{"name": "Alice", "dept": "Eng"}], "Sales": [{"name": "Bob", "dept": "Sales"}]}
```

``` utlx
let groups = groupBy($input.orders, (o) -> o.status)
{
  byStatus: groups,
  summary: entries(groups) |> map((entry) -> {
    status: entry[0],
    count: count(entry[1]),
    total: sum(map(entry[1], (o) -> o.amount))
  })
}
```

---
title: countBy
description: "countBy — UTL-X Array function. Count elements in an array that match a predicate."
pageClass: stdlib-page
---

# countBy

<p class="stdlib-meta"><code>countBy(array, predicate) → number</code> · <a href="/reference/stdlib#array">Array</a></p>

Count elements in an array that match a predicate.

- `array` (required): the array to count

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '{"orders": [{"status": "ACTIVE"}, {"status": "CLOSED"}, {"status": "ACTIVE"}]}' \
  | utlx -e 'countBy($input.orders, (o) -> o.status == "ACTIVE")'
# 2
```

``` utlx
let active = filter($input.orders, (o) -> o.status == "ACTIVE")
{
  activeCount: count(active),
  closedCount: countBy($input.orders, (o) -> o.status == "CLOSED"),
  activeNames: map(active, (o) -> o.name)
}
```

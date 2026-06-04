---
title: count
description: "count — UTL-X Array function. Count all elements in an array."
pageClass: stdlib-page
---

# count

<p class="stdlib-meta"><code>count(array) → number</code> · <a href="/reference/stdlib#array">Array</a></p>

Count all elements in an array.

- `array` (required): the array to count

``` bash
echo '{"orders": [{"status": "ACTIVE"}, {"status": "CLOSED"}, {"status": "ACTIVE"}]}' \
  | utlx -e 'count($input.orders)'
# 3
```

``` utlx
{
  totalOrders: count($input.orders),
  totalItems: count($input.items)
}
```

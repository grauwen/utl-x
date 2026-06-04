---
title: any
description: "any — UTL-X Array function. Returns true if at least ONE element satisfies the predicate. Returns"
pageClass: stdlib-page
---

# any

<p class="stdlib-meta"><code>any(array, predicate) → boolean</code> · <a href="/reference/stdlib#array">Array</a></p>

Returns true if at least ONE element satisfies the predicate. Returns
false for empty arrays.

- `array` (required): the array to test

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '{"orders": [{"status": "PENDING"}, {"status": "SHIPPED"}, {"status": "PENDING"}]}' \
  | utlx -e 'any($input.orders, (o) -> o.status == "SHIPPED")'
# true
```

``` utlx
{
  hasShipped: any($input.orders, (o) -> o.status == "SHIPPED"),
  hasCancelled: any($input.orders, (o) -> o.status == "CANCELLED")
}
// Output: {"hasShipped": true, "hasCancelled": false}
```

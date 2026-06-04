---
title: distinctBy
description: "distinctBy — UTL-X Array function. Remove duplicate values from an array using a key extractor to determine"
pageClass: stdlib-page
---

# distinctBy

<p class="stdlib-meta"><code>distinctBy(array, keyFn) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Remove duplicate values from an array using a key extractor to determine
uniqueness. Keeps the first element for each key.

- `array` (required): the array to deduplicate

- `keyFn` (required): lambda `(element) -> key`

``` bash
echo '{"orders": [{"id": 1, "cust": "C-42"}, {"id": 2, "cust": "C-42"}, {"id": 3, "cust": "C-41"}]}' \
  | utlx -e 'distinctBy($input.orders, (o) -> o.cust)'
# [{"id": 1, "cust": "C-42"}, {"id": 3, "cust": "C-41"}]
```

``` utlx
{
  onePerCustomer: distinctBy($input.orders, (o) -> o.customerId)
}
// keeps first order per customer, removes duplicates
```

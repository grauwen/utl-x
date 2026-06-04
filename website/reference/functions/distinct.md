---
title: distinct
description: "distinct — UTL-X Array function. Remove duplicate values from an array using value equality."
pageClass: stdlib-page
---

# distinct

<p class="stdlib-meta"><code>distinct(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Remove duplicate values from an array using value equality.

- `array` (required): the array to deduplicate

``` bash
echo '{"tags": ["a", "b", "a", "c", "b"]}' | utlx -e 'distinct($input.tags)'
# ["a", "b", "c"]
```

``` utlx
{
  uniqueCustomers: distinct(map($input.orders, (o) -> o.customerId)),
  uniqueStatuses: distinct(map($input.orders, (o) -> o.status))
}
```

---
title: contains
description: "contains — UTL-X String function. Check if a string contains a substring, or an array contains a value."
pageClass: stdlib-page
---

# contains

<p class="stdlib-meta"><code>contains(haystack, needle) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Check if a string contains a substring, or an array contains a value.

- `haystack` (required): the string or array to search in

- `needle` (required): the value to search for

``` bash
echo '{"roles": ["admin", "editor", "viewer"]}' \
  | utlx -e 'contains($input.roles, "admin")'
# true
```

``` utlx
{
  hasWorld: contains("Hello World", "World"),    // true (string variant)
  isAdmin: contains($input.roles, "admin"),     // true (array variant)
  activeOrders: filter($input.orders, (o) -> contains(["ACTIVE", "PENDING"], o.status))
}
```

---
title: startsWith
description: "startsWith — UTL-X String function. Check if a string starts with a given substring."
pageClass: stdlib-page
---

# startsWith

<p class="stdlib-meta"><code>startsWith(string, prefix) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Check if a string starts with a given substring.

- `string` (required): the string to test

- `prefix` (required): the substring to check for

``` bash
echo '{"orderId": "ORD-001"}' | utlx -e 'startsWith($input.orderId, "ORD-")'
# true
```

``` utlx
if (!startsWith($input.id, "ORD-")) error("Invalid order ID format")
{
  validFormat: startsWith($input.orderId, "ORD-"),
  orderId: $input.orderId
}
```

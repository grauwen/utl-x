---
title: includes
description: "includes — UTL-X Array function. Check if an array contains a specific value (strict equality)."
pageClass: stdlib-page
---

# includes

<p class="stdlib-meta"><code>includes(array, value) → boolean</code> · <a href="/reference/stdlib#array">Array</a></p>

Check if an array contains a specific value (strict equality).

- `array` (required): array to search

- `value` (required): value to find

``` bash
echo '{"tags": ["urgent", "billing", "support"]}' | utlx -e 'includes($input.tags, "urgent")'
# true
```

``` utlx
{
  isUrgent: includes($input.tags, "urgent"),
  isVIP: includes($input.roles, "VIP")
}
```

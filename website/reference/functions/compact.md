---
title: compact
description: "compact — UTL-X Array function. Remove null, empty string, and false values from an array."
pageClass: stdlib-page
---

# compact

<p class="stdlib-meta"><code>compact(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Remove null, empty string, and false values from an array.

- `array` (required): the array to compact

``` bash
echo '{"tags": ["urgent", null, "", "review", null, "important"]}' \
  | utlx -e 'compact($input.tags)'
# ["urgent", "review", "important"]
```

``` utlx
{
  contacts: compact([
    $input.name,
    $input.email,
    $input.phone
  ])
}
// null values are removed from the resulting array
```

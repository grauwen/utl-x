---
title: isNumeric
description: "isNumeric — UTL-X String function. Returns true if the string contains only numeric digits (0-9)."
pageClass: stdlib-page
---

# isNumeric

<p class="stdlib-meta"><code>isNumeric(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains only numeric digits (0-9).

- `string` (required): string to test

``` bash
echo '{"zip": "12345"}' | utlx -e 'isNumeric($input.zip)'
# true
```

``` utlx
{
  validZip: isNumeric($input.zipCode),
  numericId: isNumeric($input.id)
}
```

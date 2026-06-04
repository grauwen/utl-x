---
title: hasNumeric
description: "hasNumeric — UTL-X String function. Returns true if the string contains at least one numeric digit."
pageClass: stdlib-page
---

# hasNumeric

<p class="stdlib-meta"><code>hasNumeric(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains at least one numeric digit.

- `string` (required): string to check

``` bash
echo '{"code": "ABC123"}' | utlx -e 'hasNumeric($input.code)'
# true
```

``` utlx
{
  hasDigits: hasNumeric($input.password),
  alphaOnly: !hasNumeric($input.name)
}
```

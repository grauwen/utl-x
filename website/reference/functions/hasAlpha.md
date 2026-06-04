---
title: hasAlpha
description: "hasAlpha — UTL-X String function. Returns true if the string contains at least one alphabetic character."
pageClass: stdlib-page
---

# hasAlpha

<p class="stdlib-meta"><code>hasAlpha(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains at least one alphabetic character.

- `string` (required): string to check

``` bash
echo '{"code": "ABC123"}' | utlx -e 'hasAlpha($input.code)'
# true
```

``` utlx
{
  hasLetters: hasAlpha($input.value),
  numericOnly: !hasAlpha($input.code)
}
```

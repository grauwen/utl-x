---
title: isAlpha
description: "isAlpha — UTL-X String function. Returns true if the string contains only alphabetic characters (A-Z,"
pageClass: stdlib-page
---

# isAlpha

<p class="stdlib-meta"><code>isAlpha(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains only alphabetic characters (A-Z,
a-z, Unicode letters).

- `string` (required): string to test

``` bash
echo '{"name": "Alice"}' | utlx -e 'isAlpha($input.name)'
# true
```

``` utlx
{
  validName: isAlpha($input.firstName),
  hasSpecialChars: !isAlpha($input.input)
}
```

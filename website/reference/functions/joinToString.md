---
title: joinToString
description: "joinToString — UTL-X String function. Join array elements into a string with optional separator (defaults to"
pageClass: stdlib-page
---

# joinToString

<p class="stdlib-meta"><code>joinToString(array, separator?) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Join array elements into a string with optional separator (defaults to
`","`). Alias-style alternative to `join()`.

- `array` (required): array of values to join

- `separator` (optional): delimiter string, defaults to `","`

``` bash
echo '["a", "b", "c"]' | utlx -e 'joinToString($input, " - ")'
# a - b - c
```

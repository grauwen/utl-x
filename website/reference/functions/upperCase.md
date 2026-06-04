---
title: upperCase
description: "upperCase — UTL-X String function. Convert a string to all uppercase."
pageClass: stdlib-page
---

# upperCase

<p class="stdlib-meta"><code>upperCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to all uppercase.

- `string` (required): the string to convert

``` utlx
{
  result: upperCase("Hello World"),          // "HELLO WORLD"

  // Use case: normalize API field names
  normalized: mapKeys($input, (key) -> camelCase(key))
  // {"first_name": "Alice"} -> {"firstName": "Alice"}
}
```

## M

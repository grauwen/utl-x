---
title: isAlphanumeric
description: "isAlphanumeric — UTL-X String function. Returns true if the string contains only alphanumeric characters (A-Z,"
pageClass: stdlib-page
---

# isAlphanumeric

<p class="stdlib-meta"><code>isAlphanumeric(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains only alphanumeric characters (A-Z,
a-z, 0-9, Unicode letters).

- `string` (required): string to test

``` utlx
isAlphanumeric("Hello123")               // true
isAlphanumeric("Hello 123")              // false (contains space)
{
  validCode: isAlphanumeric($input.productCode)
}
```

---
title: isUpperCase
description: "isUpperCase — UTL-X String function. Returns true if all alphabetic characters in the string are uppercase."
pageClass: stdlib-page
---

# isUpperCase

<p class="stdlib-meta"><code>isUpperCase(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if all alphabetic characters in the string are uppercase.
Non-alphabetic characters are ignored.

- `string` (required): string to test

``` utlx
isUpperCase("HELLO")                     // true
isUpperCase("Hello")                     // false
isUpperCase("ABC123")                    // true (digits ignored)
{
  isUpper: isUpperCase($input.countryCode)
}
```

---
title: isLowerCase
description: "isLowerCase — UTL-X String function. Returns true if all alphabetic characters in the string are lowercase."
pageClass: stdlib-page
---

# isLowerCase

<p class="stdlib-meta"><code>isLowerCase(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if all alphabetic characters in the string are lowercase.
Non-alphabetic characters are ignored.

- `string` (required): string to test

``` utlx
isLowerCase("hello")                     // true
isLowerCase("Hello")                     // false
isLowerCase("hello123")                  // true (digits ignored)
{
  isLower: isLowerCase($input.code)
}
```

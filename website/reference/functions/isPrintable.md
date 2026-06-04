---
title: isPrintable
description: "isPrintable — UTL-X String function. Returns true if the string contains only printable characters (letters,"
pageClass: stdlib-page
---

# isPrintable

<p class="stdlib-meta"><code>isPrintable(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains only printable characters (letters,
digits, punctuation, spaces).

- `string` (required): string to test

``` utlx
isPrintable("Hello, World!")             // true
isPrintable("Hello\x00World")            // false (contains null byte)
{
  safe: isPrintable($input.userInput)
}
```

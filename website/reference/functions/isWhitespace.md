---
title: isWhitespace
description: "isWhitespace — UTL-X String function. Returns true if the string contains only whitespace characters (spaces,"
pageClass: stdlib-page
---

# isWhitespace

<p class="stdlib-meta"><code>isWhitespace(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains only whitespace characters (spaces,
tabs, newlines).

- `string` (required): string to test

``` utlx
isWhitespace("   ")                      // true
isWhitespace(" \t\n ")                   // true
isWhitespace("  a  ")                    // false
{
  blank: isWhitespace($input.field)
}
```

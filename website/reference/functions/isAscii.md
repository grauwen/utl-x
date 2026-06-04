---
title: isAscii
description: "isAscii — UTL-X String function. Returns true if the string contains only ASCII characters (code points"
pageClass: stdlib-page
---

# isAscii

<p class="stdlib-meta"><code>isAscii(string) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Returns true if the string contains only ASCII characters (code points
0-127).

- `string` (required): string to test

``` utlx
isAscii("Hello!")                        // true
isAscii("cafe\u0301")                    // false (contains accent)
{
  asciiSafe: isAscii($input.text)
}
```

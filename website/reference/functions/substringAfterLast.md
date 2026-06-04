---
title: substringAfterLast
description: "substringAfterLast — UTL-X String function. Return the part of a string after the LAST occurrence of a delimiter."
pageClass: stdlib-page
---

# substringAfterLast

<p class="stdlib-meta"><code>substringAfterLast(string, delimiter) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Return the part of a string after the LAST occurrence of a delimiter.

- `string` (required): the source string

- `delimiter` (required): the delimiter to search for

``` utlx
substringAfterLast("a.b.c.d", ".")       // "d"
substringAfterLast("/usr/local/bin", "/") // "bin"
```

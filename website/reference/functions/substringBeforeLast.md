---
title: substringBeforeLast
description: "substringBeforeLast — UTL-X String function. Return the part of a string before the LAST occurrence of a delimiter."
pageClass: stdlib-page
---

# substringBeforeLast

<p class="stdlib-meta"><code>substringBeforeLast(string, delimiter) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Return the part of a string before the LAST occurrence of a delimiter.

- `string` (required): the source string

- `delimiter` (required): the delimiter to search for

``` utlx
substringBeforeLast("a.b.c.d", ".")      // "a.b.c"
substringBeforeLast("file.tar.gz", ".")  // "file.tar"
```

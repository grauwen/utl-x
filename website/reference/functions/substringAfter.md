---
title: substringAfter
description: "substringAfter — UTL-X String function. Return the part of a string after the first occurrence of a delimiter."
pageClass: stdlib-page
---

# substringAfter

<p class="stdlib-meta"><code>substringAfter(string, delimiter) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Return the part of a string after the first occurrence of a delimiter.
Returns empty string if delimiter is not found.

- `string` (required): the source string

- `delimiter` (required): the delimiter to search for

``` utlx
substringAfter("user@example.com", "@")  // "example.com"
substringAfter("no-delimiter", "@")      // "" (not found — returns empty)
```

Also: `substringAfterLast("a.b.c.d", ".")` returns `"d"`.

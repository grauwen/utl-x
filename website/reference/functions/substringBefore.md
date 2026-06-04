---
title: substringBefore
description: "substringBefore — UTL-X String function. Return the part of a string before the first occurrence of a delimiter."
pageClass: stdlib-page
---

# substringBefore

<p class="stdlib-meta"><code>substringBefore(string, delimiter) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Return the part of a string before the first occurrence of a delimiter.
Returns the full string if delimiter is not found.

- `string` (required): the source string

- `delimiter` (required): the delimiter to search for

``` utlx
substringBefore("user@example.com", "@") // "user"
substringBefore("no-delimiter", "@")     // "no-delimiter" (not found — returns all)
```

Also: `substringBeforeLast("a.b.c.d", ".")` returns `"a.b.c"`.

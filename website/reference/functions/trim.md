---
title: trim
description: "trim — UTL-X String function. Remove whitespace from both ends of a string."
pageClass: stdlib-page
---

# trim

<p class="stdlib-meta"><code>trim(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Remove whitespace from both ends of a string.

- `string` (required): the string to trim

``` utlx
trim("   hello   ")                      // "hello"

// Use case: clean up CSV values
map($input, (row) -> mapValues(row, (v) -> if (isString(v)) trim(v) else v))
```

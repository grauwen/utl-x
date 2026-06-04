---
title: remove
description: "remove — UTL-X String function. Remove all occurrences of a substring from a string."
pageClass: stdlib-page
---

# remove

<p class="stdlib-meta"><code>remove(string, search) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Remove all occurrences of a substring from a string.

- `string` (required): the source string

- `search` (required): substring to remove

``` utlx
remove("Hello World", " ")              // "HelloWorld"
remove("$1,234.56", ",")                // "$1234.56"
```

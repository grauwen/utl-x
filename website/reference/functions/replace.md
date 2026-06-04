---
title: replace
description: "replace — UTL-X String function. Replace all literal occurrences of a substring."
pageClass: stdlib-page
---

# replace

<p class="stdlib-meta"><code>replace(string, search, replacement) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Replace all literal occurrences of a substring.

- `string` (required): the string to modify

- `search` (required): literal substring to find

- `replacement` (required): what to replace with

``` utlx
replace("Hello World", "World", "UTL-X")   // "Hello UTL-X"
replace("2026-05-01", "-", "/")             // "2026/05/01" (replaces ALL occurrences)
```

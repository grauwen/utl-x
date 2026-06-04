---
title: regexGroups
description: "regexGroups — UTL-X String function. Extract all capture groups from the first match of a regex pattern."
pageClass: stdlib-page
---

# regexGroups

<p class="stdlib-meta"><code>regexGroups(string, pattern) → array</code> · <a href="/reference/stdlib#string">String</a></p>

Extract all capture groups from the first match of a regex pattern.

- `string` (required): the string to search

- `pattern` (required): regex with capture groups

``` utlx
regexGroups("2026-05-01", "(\\d{4})-(\\d{2})-(\\d{2})")
// ["2026", "05", "01"]
```

---
title: regexNamedGroups
description: "regexNamedGroups — UTL-X String function. Extract named capture groups from the first match of a regex pattern."
pageClass: stdlib-page
---

# regexNamedGroups

<p class="stdlib-meta"><code>regexNamedGroups(string, pattern) → object</code> · <a href="/reference/stdlib#string">String</a></p>

Extract named capture groups from the first match of a regex pattern.

- `string` (required): the string to search

- `pattern` (required): regex with named groups `(?<name>...)`

``` utlx
regexNamedGroups("2026-05-01", "(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})")
// {"year": "2026", "month": "05", "day": "01"}
```

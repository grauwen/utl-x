---
title: isDate
description: "isDate — UTL-X Type function. Returns true if the value is a date (not a string representation of a"
pageClass: stdlib-page
---

# isDate

<p class="stdlib-meta"><code>isDate(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is a date (not a string representation of a
date).

- `value` (required): the value to test

``` utlx
isDate(parseDate("2026-05-01", "yyyy-MM-dd"))       // true
isDate("2026-05-01")                                  // false (string, not date)
```

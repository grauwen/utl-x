---
title: daysBetween
description: "daysBetween — UTL-X Date & Time function. Alias for diffDays(). Calculate the difference in days between two"
pageClass: stdlib-page
---

# daysBetween

<p class="stdlib-meta"><code>daysBetween(date1, date2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Alias for `diffDays()`. Calculate the difference in days between two
dates.

- `date1` (required): start date

- `date2` (required): end date

``` utlx
let start = parseDate("2026-05-01", "yyyy-MM-dd")
let end = parseDate("2026-05-15", "yyyy-MM-dd")
{
  days: daysBetween(start, end)    // 14
}
```

---
title: diffYears
description: "diffYears — UTL-X Date & Time function. Difference in years between two dates."
pageClass: stdlib-page
---

# diffYears

<p class="stdlib-meta"><code>diffYears(date1, date2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Difference in years between two dates.

- `date1` (required): start date

- `date2` (required): end date

``` utlx
let start = parseDate("2020-01-01", "yyyy-MM-dd")
let end = parseDate("2026-05-01", "yyyy-MM-dd")
{
  years: diffYears(start, end)    // 6
}
```

---
title: diffMonths
description: "diffMonths — UTL-X Date & Time function. Difference in months between two dates (approximate whole months)."
pageClass: stdlib-page
---

# diffMonths

<p class="stdlib-meta"><code>diffMonths(date1, date2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Difference in months between two dates (approximate whole months).

- `date1` (required): start date

- `date2` (required): end date

``` utlx
let start = parseDate("2026-01-15", "yyyy-MM-dd")
let end = parseDate("2026-05-15", "yyyy-MM-dd")
{
  months: diffMonths(start, end)    // 4
}
```

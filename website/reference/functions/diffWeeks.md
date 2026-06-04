---
title: diffWeeks
description: "diffWeeks — UTL-X Date & Time function. Difference in weeks between two dates."
pageClass: stdlib-page
---

# diffWeeks

<p class="stdlib-meta"><code>diffWeeks(date1, date2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Difference in weeks between two dates.

- `date1` (required): start date

- `date2` (required): end date

``` utlx
let start = parseDate("2026-05-01", "yyyy-MM-dd")
let end = parseDate("2026-05-22", "yyyy-MM-dd")
{
  weeks: diffWeeks(start, end)    // 3
}
```

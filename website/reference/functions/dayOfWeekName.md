---
title: dayOfWeekName
description: "dayOfWeekName — UTL-X Date & Time function. Return the day of the week as a name (e.g., 'Thursday')."
pageClass: stdlib-page
---

# dayOfWeekName

<p class="stdlib-meta"><code>dayOfWeekName(date) → string</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Return the day of the week as a name (e.g., "Thursday").

- `date` (required): a date or datetime value

``` utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
{
  weekdayName: dayOfWeekName(d)      // "Thursday"
}
```

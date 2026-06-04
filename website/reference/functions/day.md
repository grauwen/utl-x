---
title: day
description: "day — UTL-X Date & Time function. Extract the day-of-month component (1-31) from a date."
pageClass: stdlib-page
---

# day

<p class="stdlib-meta"><code>day(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Extract the day-of-month component (1-31) from a date.

- `date` (required): a date or datetime value

``` utlx
{
  dayOfMonth: day(parseDate("2026-05-15", "yyyy-MM-dd")),    // 15
  today: day(now())                                          // current day of month
}
```

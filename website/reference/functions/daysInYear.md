---
title: daysInYear
description: "daysInYear — UTL-X Date & Time function. Get the number of days in the year of the given date (365 or 366 for"
pageClass: stdlib-page
---

# daysInYear

<p class="stdlib-meta"><code>daysInYear(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the number of days in the year of the given date (365 or 366 for
leap years).

- `date` (required): a date or datetime value

``` utlx
{
  y2026: daysInYear(parseDate("2026-01-01", "yyyy-MM-dd")),    // 365
  y2024: daysInYear(parseDate("2024-01-01", "yyyy-MM-dd"))     // 366 (leap year)
}
```

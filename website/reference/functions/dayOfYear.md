---
title: dayOfYear
description: "dayOfYear — UTL-X Date & Time function. Return the day number within the year (1-365 or 1-366 for leap years)."
pageClass: stdlib-page
---

# dayOfYear

<p class="stdlib-meta"><code>dayOfYear(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Return the day number within the year (1-365 or 1-366 for leap years).

- `date` (required): a date or datetime value

``` utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
{
  ordinalDay: dayOfYear(d)          // 121 (121st day of 2026)
}
```

Also: `daysInMonth(year, month)` → `daysInMonth(2026, 2)` returns `28`.
`daysInYear(year)` → `daysInYear(2024)` returns `366` (leap year).
`isLeapYear(year)`.

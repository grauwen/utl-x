---
title: daysInMonth
description: "daysInMonth — UTL-X Date & Time function. Get the number of days in the month of the given date."
pageClass: stdlib-page
---

# daysInMonth

<p class="stdlib-meta"><code>daysInMonth(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the number of days in the month of the given date.

- `date` (required): a date or datetime value

``` utlx
{
  feb2026: daysInMonth(parseDate("2026-02-01", "yyyy-MM-dd")),   // 28
  feb2024: daysInMonth(parseDate("2024-02-01", "yyyy-MM-dd")),   // 29 (leap year)
  jan: daysInMonth(parseDate("2026-01-15", "yyyy-MM-dd"))        // 31
}
```

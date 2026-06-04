---
title: isBetween
description: "isBetween — UTL-X Date & Time function. Check if a date is between two other dates (inclusive)."
pageClass: stdlib-page
---

# isBetween

<p class="stdlib-meta"><code>isBetween(date, start, end) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Check if a date is between two other dates (inclusive).

- `date` (required): date to test

- `start` (required): range start date

- `end` (required): range end date

``` utlx
{
  inRange: isBetween(now(), $input.startDate, $input.endDate),
  inQ1: isBetween($input.date, parseDate("2026-01-01", "yyyy-MM-dd"), parseDate("2026-03-31", "yyyy-MM-dd"))
}
```

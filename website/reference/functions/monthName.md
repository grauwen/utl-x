---
title: monthName
description: "monthName — UTL-X Date & Time function. Get the full month name from a date or datetime value."
pageClass: stdlib-page
---

# monthName

<p class="stdlib-meta"><code>monthName(date) → string</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the full month name from a date or datetime value.

- `date` (required): date or datetime value

``` utlx
{
  current: monthName(parseDate("2026-05-01")),   // "May"
  xmas: monthName(parseDate("2026-12-25"))       // "December"
}
```

## N

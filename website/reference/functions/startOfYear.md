---
title: startOfYear
description: "startOfYear — UTL-X Date & Time function. Get the first day of the year for the given date."
pageClass: stdlib-page
---

# startOfYear

<p class="stdlib-meta"><code>startOfYear(datetime) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the first day of the year for the given date.

- `datetime` (required): a date or datetime value

``` utlx
startOfYear(parseDate("2026-05-15", "yyyy-MM-dd"))
// 2026-01-01T00:00:00
```

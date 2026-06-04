---
title: startOfMonth
description: "startOfMonth — UTL-X Date & Time function. Get the first day of the month for the given date."
pageClass: stdlib-page
---

# startOfMonth

<p class="stdlib-meta"><code>startOfMonth(datetime) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the first day of the month for the given date.

- `datetime` (required): a date or datetime value

``` utlx
startOfMonth(parseDate("2026-05-15", "yyyy-MM-dd"))
// 2026-05-01T00:00:00
```

---
title: startOfQuarter
description: "startOfQuarter — UTL-X Date & Time function. Get the first day of the quarter for the given date."
pageClass: stdlib-page
---

# startOfQuarter

<p class="stdlib-meta"><code>startOfQuarter(datetime) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the first day of the quarter for the given date.

- `datetime` (required): a date or datetime value

``` utlx
startOfQuarter(parseDate("2026-05-15", "yyyy-MM-dd"))
// 2026-04-01T00:00:00
```

---
title: addWeeks
description: "addWeeks — UTL-X Date & Time function. Add (or subtract) weeks to a date. Equivalent to"
pageClass: stdlib-page
---

# addWeeks

<p class="stdlib-meta"><code>addWeeks(date, count) → date</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Add (or subtract) weeks to a date. Equivalent to
`addDays(date, count * 7)`.

- `date` (required): the starting date or datetime

- `count` (required): number of weeks to add. Negative to subtract.

``` utlx
addWeeks(parseDate("2026-05-01", "yyyy-MM-dd"), 2)     // 2026-05-15
addWeeks(parseDate("2026-05-01", "yyyy-MM-dd"), -1)    // 2026-04-24
```

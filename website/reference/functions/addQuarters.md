---
title: addQuarters
description: "addQuarters — UTL-X Date & Time function. Add (or subtract) quarters (3-month periods) to a date."
pageClass: stdlib-page
---

# addQuarters

<p class="stdlib-meta"><code>addQuarters(date, count) → date</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Add (or subtract) quarters (3-month periods) to a date.

- `date` (required): the starting date or datetime

- `count` (required): number of quarters to add. Negative to subtract.

``` utlx
addQuarters(parseDate("2026-01-15", "yyyy-MM-dd"), 1)  // 2026-04-15
addQuarters(parseDate("2026-01-15", "yyyy-MM-dd"), 4)  // 2027-01-15 (1 year)
```

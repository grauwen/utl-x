---
title: addYears
description: "addYears — UTL-X Date & Time function. Add (or subtract) years to a date. Handles leap year edge cases."
pageClass: stdlib-page
---

# addYears

<p class="stdlib-meta"><code>addYears(date, count) → date</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Add (or subtract) years to a date. Handles leap year edge cases.

- `date` (required): the starting date or datetime

- `count` (required): number of years to add. Negative to subtract.

``` utlx
addYears(parseDate("2026-05-01", "yyyy-MM-dd"), 1)     // 2027-05-01
addYears(parseDate("2026-05-01", "yyyy-MM-dd"), -10)   // 2016-05-01

// Edge case: leap year
addYears(parseDate("2024-02-29", "yyyy-MM-dd"), 1)     // 2025-02-28 (2025 is not a leap year)
```

---
title: isWeekday
description: "isWeekday — UTL-X Date & Time function. Returns true if the date falls on a weekday (Monday through Friday)."
pageClass: stdlib-page
---

# isWeekday

<p class="stdlib-meta"><code>isWeekday(date) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Returns true if the date falls on a weekday (Monday through Friday).

- `date` (required): date or datetime

``` utlx
isWeekday(parseDate("2026-05-01", "yyyy-MM-dd"))  // true (Thursday)

// Use case: calculate business days
let startDate = parseDate($input.start, "yyyy-MM-dd")
let workDays = filter(
  map(range(0, 30), (i) -> addDays(startDate, i)),
  (d) -> isWeekday(d)
)
{ businessDays: count(workDays) }
```

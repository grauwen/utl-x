---
title: dayOfWeek
description: "dayOfWeek — UTL-X Date & Time function. Return the day of the week as a number (1=Monday, 7=Sunday)."
pageClass: stdlib-page
---

# dayOfWeek

<p class="stdlib-meta"><code>dayOfWeek(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Return the day of the week as a number (1=Monday, 7=Sunday).

- `date` (required): a date or datetime value

``` utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
{
  weekday: dayOfWeek(d)          // 5 (Thursday — 1=Monday, 7=Sunday)
}
```

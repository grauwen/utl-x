---
title: dayOfMonth
description: "dayOfMonth — UTL-X Date & Time function. Alias for day(). Extract the day-of-month component (1-31) from a"
pageClass: stdlib-page
---

# dayOfMonth

<p class="stdlib-meta"><code>dayOfMonth(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Alias for `day()`. Extract the day-of-month component (1-31) from a
date.

- `date` (required): a date or datetime value

``` utlx
{
  dom: dayOfMonth(parseDate("2026-05-15", "yyyy-MM-dd"))    // 15
}
```

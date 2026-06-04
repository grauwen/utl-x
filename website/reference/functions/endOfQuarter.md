---
title: endOfQuarter
description: "endOfQuarter — UTL-X Date & Time function. Get the last moment of the last day of the quarter."
pageClass: stdlib-page
---

# endOfQuarter

<p class="stdlib-meta"><code>endOfQuarter(date) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the last moment of the last day of the quarter.

- `date` (required): date or datetime

``` utlx
{
  quarterEnd: endOfQuarter(now()),
  reportDeadline: endOfQuarter(parseDate($input.date, "yyyy-MM-dd"))
}
```

---
title: endOfYear
description: "endOfYear — UTL-X Date & Time function. Get the last moment of December 31 for the given date's year."
pageClass: stdlib-page
---

# endOfYear

<p class="stdlib-meta"><code>endOfYear(date) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the last moment of December 31 for the given date's year.

- `date` (required): date or datetime

``` utlx
{
  yearEnd: endOfYear(now()),
  fiscalEnd: endOfYear(parseDate($input.startDate, "yyyy-MM-dd"))
}
```

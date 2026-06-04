---
title: endOfDay
description: "endOfDay — UTL-X Date & Time function. Get the end of day (23:59:59.999) for a given date."
pageClass: stdlib-page
---

# endOfDay

<p class="stdlib-meta"><code>endOfDay(date) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the end of day (23:59:59.999) for a given date.

- `date` (required): date or datetime

``` bash
echo '{"date": "2026-05-01"}' | utlx -e 'endOfDay(parseDate($input.date, "yyyy-MM-dd"))'
# "2026-05-01T23:59:59.999"
```

``` utlx
{
  dayEnd: endOfDay(now()),
  deadline: endOfDay(parseDate($input.dueDate, "yyyy-MM-dd"))
}
```

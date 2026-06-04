---
title: isSameDay
description: "isSameDay — UTL-X Date & Time function. Check if two dates fall on the same calendar day (ignoring time)."
pageClass: stdlib-page
---

# isSameDay

<p class="stdlib-meta"><code>isSameDay(date1, date2) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Check if two dates fall on the same calendar day (ignoring time).

- `date1` (required): first date

- `date2` (required): second date

``` utlx
{
  sameDay: isSameDay($input.created, $input.modified),
  createdToday: isSameDay($input.created, now())
}
```

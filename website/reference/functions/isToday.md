---
title: isToday
description: "isToday — UTL-X Date & Time function. Check if a date is today."
pageClass: stdlib-page
---

# isToday

<p class="stdlib-meta"><code>isToday(date) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Check if a date is today.

- `date` (required): date to check

``` utlx
{
  isNew: isToday($input.createdAt),
  todayOrders: filter($input.orders, (o) -> isToday(o.date))
}
```

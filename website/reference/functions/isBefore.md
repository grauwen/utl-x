---
title: isBefore
description: "isBefore — UTL-X Date & Time function. Check if the first date is before the second date."
pageClass: stdlib-page
---

# isBefore

<p class="stdlib-meta"><code>isBefore(date1, date2) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Check if the first date is before the second date.

- `date1` (required): date to compare

- `date2` (required): reference date

``` utlx
{
  notYetDue: isBefore(now(), parseDate($input.dueDate, "yyyy-MM-dd")),
  isPast: isBefore($input.eventDate, now())
}
```

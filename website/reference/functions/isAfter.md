---
title: isAfter
description: "isAfter — UTL-X Date & Time function. Check if the first date is after the second date."
pageClass: stdlib-page
---

# isAfter

<p class="stdlib-meta"><code>isAfter(date1, date2) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Check if the first date is after the second date.

- `date1` (required): date to compare

- `date2` (required): reference date

``` utlx
{
  isOverdue: isAfter(now(), parseDate($input.dueDate, "yyyy-MM-dd")),
  isExpired: isAfter(now(), $input.expiresAt)
}
```

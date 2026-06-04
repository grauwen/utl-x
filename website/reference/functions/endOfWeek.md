---
title: endOfWeek
description: "endOfWeek — UTL-X Date & Time function. Get the end of the week (Sunday 23:59:59.999)."
pageClass: stdlib-page
---

# endOfWeek

<p class="stdlib-meta"><code>endOfWeek(date) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the end of the week (Sunday 23:59:59.999).

- `date` (required): date or datetime

``` utlx
{
  weekEnd: endOfWeek(now())
}
```

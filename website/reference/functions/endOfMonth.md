---
title: endOfMonth
description: "endOfMonth — UTL-X Date & Time function. Get the last moment of the last day of the month."
pageClass: stdlib-page
---

# endOfMonth

<p class="stdlib-meta"><code>endOfMonth(date) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the last moment of the last day of the month.

- `date` (required): date or datetime

``` utlx
{
  monthEnd: endOfMonth(now()),
  invoiceDue: endOfMonth(parseDate($input.invoiceDate, "yyyy-MM-dd"))
}
```

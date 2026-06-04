---
title: month
description: "month — UTL-X Date & Time function. Extract the month component (1-12) from a date or datetime value."
pageClass: stdlib-page
---

# month

<p class="stdlib-meta"><code>month(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Extract the month component (1-12) from a date or datetime value.

- `date` (required): date or datetime value

``` utlx
{
  m: month(parseDate("2026-05-01"))          // 5
}
```

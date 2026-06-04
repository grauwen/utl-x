---
title: quarter
description: "quarter — UTL-X Date & Time function. Get the quarter (1-4) from a date."
pageClass: stdlib-page
---

# quarter

<p class="stdlib-meta"><code>quarter(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the quarter (1-4) from a date.

- `date` (required): a date or datetime value

``` utlx
quarter(parseDate("2026-05-01", "yyyy-MM-dd"))  // 2
```

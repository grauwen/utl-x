---
title: weekOfYear
description: "weekOfYear — UTL-X Date & Time function. Get the ISO week number (1-53) for a date."
pageClass: stdlib-page
---

# weekOfYear

<p class="stdlib-meta"><code>weekOfYear(date) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the ISO week number (1-53) for a date.

- `date` (required): a date or datetime value

``` utlx
weekOfYear(parseDate("2026-05-01", "yyyy-MM-dd"))  // 18
```

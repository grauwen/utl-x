---
title: isWeekend
description: "isWeekend — UTL-X Date & Time function. Returns true if the date falls on a weekend (Saturday or Sunday)."
pageClass: stdlib-page
---

# isWeekend

<p class="stdlib-meta"><code>isWeekend(date) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Returns true if the date falls on a weekend (Saturday or Sunday).

- `date` (required): date or datetime

``` utlx
isWeekend(parseDate("2026-05-03", "yyyy-MM-dd"))  // true (Sunday)
```

## J

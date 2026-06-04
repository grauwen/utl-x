---
title: diffHours
description: "diffHours — UTL-X Date & Time function. Difference in hours between two datetimes."
pageClass: stdlib-page
---

# diffHours

<p class="stdlib-meta"><code>diffHours(datetime1, datetime2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Difference in hours between two datetimes.

- `datetime1` (required): start datetime

- `datetime2` (required): end datetime

``` utlx
let start = parseDate("2026-05-01T08:00:00", "yyyy-MM-dd'T'HH:mm:ss")
let end = parseDate("2026-05-01T17:30:00", "yyyy-MM-dd'T'HH:mm:ss")
{
  hours: diffHours(start, end)    // 9
}
```

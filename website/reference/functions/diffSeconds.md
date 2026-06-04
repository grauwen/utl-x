---
title: diffSeconds
description: "diffSeconds — UTL-X Date & Time function. Difference in seconds between two datetimes."
pageClass: stdlib-page
---

# diffSeconds

<p class="stdlib-meta"><code>diffSeconds(datetime1, datetime2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Difference in seconds between two datetimes.

- `datetime1` (required): start datetime

- `datetime2` (required): end datetime

``` utlx
let start = parseDate("2026-05-01T08:00:00", "yyyy-MM-dd'T'HH:mm:ss")
let end = parseDate("2026-05-01T08:01:30", "yyyy-MM-dd'T'HH:mm:ss")
{
  seconds: diffSeconds(start, end)    // 90
}
```

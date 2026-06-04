---
title: diffMinutes
description: "diffMinutes — UTL-X Date & Time function. Difference in minutes between two datetimes."
pageClass: stdlib-page
---

# diffMinutes

<p class="stdlib-meta"><code>diffMinutes(datetime1, datetime2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Difference in minutes between two datetimes.

- `datetime1` (required): start datetime

- `datetime2` (required): end datetime

``` utlx
let start = parseDate("2026-05-01T08:00:00", "yyyy-MM-dd'T'HH:mm:ss")
let end = parseDate("2026-05-01T08:45:00", "yyyy-MM-dd'T'HH:mm:ss")
{
  minutes: diffMinutes(start, end)    // 45
}
```

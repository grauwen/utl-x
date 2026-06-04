---
title: fromUTC
description: "fromUTC — UTL-X Date & Time function. Convert a UTC datetime to a local datetime in the specified timezone."
pageClass: stdlib-page
---

# fromUTC

<p class="stdlib-meta"><code>fromUTC(datetime, timezone) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Convert a UTC datetime to a local datetime in the specified timezone.

- `datetime` (required): UTC datetime

- `timezone` (required): target timezone ID (e.g., "America/New_York")

``` bash
echo '{"utc": "2026-05-01T14:00:00Z"}' | utlx -e 'fromUTC(parseDate($input.utc, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"), "Europe/Amsterdam")'
# "2026-05-01T16:00:00+02:00"
```

``` utlx
{
  localTime: fromUTC(now(), "Asia/Tokyo")
}
```

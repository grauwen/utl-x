---
title: formatDateTimeInTimezone
description: "formatDateTimeInTimezone — UTL-X Date & Time function. Format a datetime value displayed in a specific timezone."
pageClass: stdlib-page
---

# formatDateTimeInTimezone

<p class="stdlib-meta"><code>formatDateTimeInTimezone(datetime, timezone) → string</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Format a datetime value displayed in a specific timezone.

- `datetime` (required): datetime value

- `timezone` (required): timezone ID (e.g., "America/New_York")

``` bash
echo '{"ts": "2026-05-01T14:30:00Z"}' | utlx -e 'formatDateTimeInTimezone(parseDate($input.ts, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"), "America/New_York")'
# "2026-05-01T10:30:00-04:00"
```

``` utlx
{
  localTime: formatDateTimeInTimezone(now(), "Europe/Amsterdam"),
  userTime: formatDateTimeInTimezone(now(), $input.userTimezone)
}
```

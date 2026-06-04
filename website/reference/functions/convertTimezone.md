---
title: convertTimezone
description: "convertTimezone — UTL-X Date & Time function. Convert a datetime from one timezone to another."
pageClass: stdlib-page
---

# convertTimezone

<p class="stdlib-meta"><code>convertTimezone(datetime, fromTz, toTz) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Convert a datetime from one timezone to another.

- `datetime` (required): the datetime value to convert

- `fromTz` (required): source timezone (e.g., `"America/New_York"`)

- `toTz` (required): target timezone (e.g., `"Europe/Amsterdam"`)

``` bash
echo '{"timestamp": "2026-05-01T09:00:00", "fromTz": "America/New_York", "toTz": "Europe/Amsterdam"}' \
  | utlx -e 'formatDate(convertTimezone(parseDate($input.timestamp, "yyyy-MM-dd'\''T'\''HH:mm:ss"), $input.fromTz, $input.toTz), "HH:mm")'
# "15:00"
```

``` utlx
{
  localTime: convertTimezone(now(), "UTC", "Europe/Amsterdam")
}
```

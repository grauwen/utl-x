---
title: parseDateTimeWithTimezone
description: "parseDateTimeWithTimezone — UTL-X Date & Time function. Parse a datetime string using a format pattern and explicit timezone."
pageClass: stdlib-page
---

# parseDateTimeWithTimezone

<p class="stdlib-meta"><code>parseDateTimeWithTimezone(string, pattern, timezone) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Parse a datetime string using a format pattern and explicit timezone.

- `string` (required): the datetime string to parse

- `pattern` (required): format pattern

- `timezone` (required): timezone ID (e.g. `"Europe/Amsterdam"`)

``` utlx
let dt = parseDateTimeWithTimezone($input.ts, "yyyy-MM-dd HH:mm:ss", "Europe/Amsterdam")
{
  utc: toUTC(dt)
}
```

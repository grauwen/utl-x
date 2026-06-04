---
title: getTimezoneOffsetHours
description: "getTimezoneOffsetHours — UTL-X Date & Time function. Get the timezone offset in hours for a given datetime or timezone."
pageClass: stdlib-page
---

# getTimezoneOffsetHours

<p class="stdlib-meta"><code>getTimezoneOffsetHours(datetime, timezone?) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the timezone offset in hours for a given datetime or timezone.

- `datetime` (required): datetime value

- `timezone` (optional): timezone ID

``` utlx
{
  offsetHours: getTimezoneOffsetHours(now(), "America/New_York")  // -4 or -5
}
```

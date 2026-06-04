---
title: toUTC
description: "toUTC — UTL-X Date & Time function. Convert a local datetime to UTC."
pageClass: stdlib-page
---

# toUTC

<p class="stdlib-meta"><code>toUTC(datetime) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Convert a local datetime to UTC.

- `datetime` (required): datetime with timezone information

``` utlx
{
  utcTime: toUTC($input.localTimestamp)
}
```

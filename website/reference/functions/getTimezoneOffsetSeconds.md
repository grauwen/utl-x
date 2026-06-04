---
title: getTimezoneOffsetSeconds
description: "getTimezoneOffsetSeconds — UTL-X Date & Time function. Get the timezone offset in seconds for a datetime value."
pageClass: stdlib-page
---

# getTimezoneOffsetSeconds

<p class="stdlib-meta"><code>getTimezoneOffsetSeconds(datetime) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the timezone offset in seconds for a datetime value.

- `datetime` (required): datetime with timezone

``` utlx
{
  offsetSecs: getTimezoneOffsetSeconds(now())   // 7200 for +02:00
}
```

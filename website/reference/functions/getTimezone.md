---
title: getTimezone
description: "getTimezone — UTL-X Date & Time function. Get the timezone offset string from a datetime value."
pageClass: stdlib-page
---

# getTimezone

<p class="stdlib-meta"><code>getTimezone(datetime) → string</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Get the timezone offset string from a datetime value.

- `datetime` (required): datetime with timezone

``` utlx
{
  offset: getTimezone(now())              // "+02:00"
}
```

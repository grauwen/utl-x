---
title: minutes
description: "minutes — UTL-X Date & Time function. Extract the minutes component (0-59) from a datetime or time value."
pageClass: stdlib-page
---

# minutes

<p class="stdlib-meta"><code>minutes(datetime) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Extract the minutes component (0-59) from a datetime or time value.

- `datetime` (required): datetime or time value

``` utlx
{
  mins: minutes(parseDate("2026-05-01T14:35:00Z"))  // 35
}
```

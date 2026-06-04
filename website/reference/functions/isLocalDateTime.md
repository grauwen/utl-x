---
title: isLocalDateTime
description: "isLocalDateTime — UTL-X Date & Time function. Check if a value is a local datetime (datetime without timezone"
pageClass: stdlib-page
---

# isLocalDateTime

<p class="stdlib-meta"><code>isLocalDateTime(value) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Check if a value is a local datetime (datetime without timezone
information).

- `value` (required): value to test

``` utlx
{
  isLocal: isLocalDateTime($input.timestamp)
}
```

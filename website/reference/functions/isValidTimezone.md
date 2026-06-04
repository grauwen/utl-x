---
title: isValidTimezone
description: "isValidTimezone — UTL-X Date & Time function. Check if a timezone ID string is valid."
pageClass: stdlib-page
---

# isValidTimezone

<p class="stdlib-meta"><code>isValidTimezone(timezone) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Check if a timezone ID string is valid.

- `timezone` (required): timezone ID to validate

``` utlx
isValidTimezone("America/New_York")      // true
isValidTimezone("Invalid/Zone")          // false
{
  valid: isValidTimezone($input.userTimezone)
}
```

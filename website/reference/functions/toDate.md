---
title: toDate
description: "toDate — UTL-X Date & Time function. Convert a string to a date. Alias for parseDate with single argument"
pageClass: stdlib-page
---

# toDate

<p class="stdlib-meta"><code>toDate(string) → date</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Convert a string to a date. Alias for `parseDate` with single argument
(ISO 8601 auto-detection).

- `string` (required): date string in ISO format

``` utlx
toDate("2026-05-01")                     // date value
```

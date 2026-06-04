---
title: currentDate
description: "currentDate — UTL-X Date & Time function. Return the current date (no time component)."
pageClass: stdlib-page
---

# currentDate

<p class="stdlib-meta"><code>currentDate() → date</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Return the current date (no time component).

``` utlx
{
  today: currentDate(),                      // 2026-05-01 (date only, no time)
  formatted: formatDate(currentDate(), "yyyy-MM-dd")  // "2026-05-01"
}
```

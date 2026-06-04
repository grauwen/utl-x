---
title: addHours
description: "addHours — UTL-X Date & Time function. Add (or subtract) hours to a datetime."
pageClass: stdlib-page
---

# addHours

<p class="stdlib-meta"><code>addHours(datetime, count) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Add (or subtract) hours to a datetime.

- `datetime` (required): the starting datetime

- `count` (required): number of hours to add. Negative to subtract.

``` utlx
addHours(now(), 3)                       // 3 hours from now
addHours(now(), -24)                     // 24 hours ago (same as yesterday, same time)
```

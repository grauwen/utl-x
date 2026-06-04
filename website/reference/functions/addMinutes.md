---
title: addMinutes
description: "addMinutes — UTL-X Date & Time function. Add (or subtract) minutes to a datetime."
pageClass: stdlib-page
---

# addMinutes

<p class="stdlib-meta"><code>addMinutes(datetime, count) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Add (or subtract) minutes to a datetime.

- `datetime` (required): the starting datetime

- `count` (required): number of minutes to add. Negative to subtract.

``` utlx
addMinutes(now(), 90)                    // 1.5 hours from now
addMinutes(now(), -30)                   // 30 minutes ago
```

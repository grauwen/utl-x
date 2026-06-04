---
title: addSeconds
description: "addSeconds — UTL-X Date & Time function. Add (or subtract) seconds to a datetime."
pageClass: stdlib-page
---

# addSeconds

<p class="stdlib-meta"><code>addSeconds(datetime, count) → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Add (or subtract) seconds to a datetime.

- `datetime` (required): the starting datetime

- `count` (required): number of seconds to add. Negative to subtract.

``` utlx
addSeconds(now(), 3600)                  // 1 hour from now (3600 seconds)
addSeconds(now(), -1)                    // 1 second ago
```

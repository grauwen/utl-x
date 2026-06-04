---
title: now
description: "now — UTL-X Date & Time function. Return the current UTC datetime."
pageClass: stdlib-page
---

# now

<p class="stdlib-meta"><code>now() → datetime</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Return the current UTC datetime.

``` utlx
// Use case: add timestamp to output
{
  ...$input,
  timestamp: now(),                          // 2026-05-01T14:30:00Z (UTC datetime)
  processedAt: formatDate(now(), "yyyy-MM-dd'T'HH:mm:ss'Z'")
}
```

---
title: timerStats
description: "timerStats — UTL-X System function. Get statistics for a named timer (min, max, avg, count)."
pageClass: stdlib-page
---

# timerStats

<p class="stdlib-meta"><code>timerStats(name) → object</code> · <a href="/reference/stdlib#system">System</a></p>

Get statistics for a named timer (min, max, avg, count).

- `name` (required): timer name

``` utlx
let stats = timerStats("transform")
// {min: 12, max: 45, avg: 28, count: 100}
```

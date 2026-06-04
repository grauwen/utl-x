---
title: timerCheck
description: "timerCheck — UTL-X System function. Get elapsed time of a named timer without stopping it."
pageClass: stdlib-page
---

# timerCheck

<p class="stdlib-meta"><code>timerCheck(name) → number</code> · <a href="/reference/stdlib#system">System</a></p>

Get elapsed time of a named timer without stopping it.

- `name` (required): timer name

``` utlx
let elapsed = timerCheck("process")      // milliseconds since start
```

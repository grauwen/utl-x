---
title: timerStop
description: "timerStop — UTL-X System function. Stop a named timer and return elapsed time in milliseconds."
pageClass: stdlib-page
---

# timerStop

<p class="stdlib-meta"><code>timerStop(name) → number</code> · <a href="/reference/stdlib#system">System</a></p>

Stop a named timer and return elapsed time in milliseconds.

- `name` (required): timer name

``` utlx
let elapsed = timerStop("transform")     // e.g. 42
```

---
title: timerReset
description: "timerReset — UTL-X System function. Reset a named timer to zero without stopping it."
pageClass: stdlib-page
---

# timerReset

<p class="stdlib-meta"><code>timerReset(name) → null</code> · <a href="/reference/stdlib#system">System</a></p>

Reset a named timer to zero without stopping it.

- `name` (required): timer name

``` utlx
let _ = timerReset("process")
```

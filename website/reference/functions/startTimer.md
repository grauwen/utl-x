---
title: startTimer
description: "startTimer — UTL-X System function. Start a timer to measure execution time. Use with endTimer() to log"
pageClass: stdlib-page
---

# startTimer

<p class="stdlib-meta"><code>startTimer() → null</code> · <a href="/reference/stdlib#system">System</a></p>

Start a timer to measure execution time. Use with `endTimer()` to log
elapsed time.

``` utlx
let _ = startTimer()
// ... expensive operations ...
let _ = endTimer()
```

---
title: endTimer
description: "endTimer — UTL-X System function. Logs elapsed time since startTimer() was called with the same label."
pageClass: stdlib-page
---

# endTimer

<p class="stdlib-meta"><code>endTimer(label) → number</code> · <a href="/reference/stdlib#system">System</a></p>

Logs elapsed time since `startTimer()` was called with the same label.
Returns elapsed milliseconds.

- `label` (required): timer label (must match a previous `startTimer`
  call)

``` utlx
startTimer("transform")
let result = map($input.items, (i) -> { name: upperCase(i.name) })
endTimer("transform")
result
```

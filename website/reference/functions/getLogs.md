---
title: getLogs
description: "getLogs — UTL-X System function. Retrieves all log entries that have been recorded during the current"
pageClass: stdlib-page
---

# getLogs

<p class="stdlib-meta"><code>getLogs() → array</code> · <a href="/reference/stdlib#system">System</a></p>

Retrieves all log entries that have been recorded during the current
transformation.

``` utlx
info("Processing started")
let result = map($input.items, (i) -> i.name)
info("Processing complete")
{
  result: result,
  logs: getLogs()
}
```

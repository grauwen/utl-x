---
title: info
description: "info — UTL-X System function. Log a message at INFO level. Returns null (passthrough for pipeline"
pageClass: stdlib-page
---

# info

<p class="stdlib-meta"><code>info(message) → null</code> · <a href="/reference/stdlib#system">System</a></p>

Log a message at INFO level. Returns null (passthrough for pipeline
usage).

- `message` (required): message to log

``` utlx
info("Starting transformation")
let result = map($input.items, (i) -> i.name)
info(concat("Processed ", toString(count(result)), " items"))
{
  items: result
}
// Logs: "Starting transformation", "Processed 2 items"
// Output: {"items": ["A", "B"]}
```

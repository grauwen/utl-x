---
title: trace
description: "trace — UTL-X System function. Log a value with TRACE level and return it (passthrough for debugging in"
pageClass: stdlib-page
---

# trace

<p class="stdlib-meta"><code>trace(value) → value</code> · <a href="/reference/stdlib#system">System</a></p>

Log a value with TRACE level and return it (passthrough for debugging in
pipelines).

- `value` (required): value to log and pass through

``` utlx
let result = trace($input.data)          // logs and passes through
```

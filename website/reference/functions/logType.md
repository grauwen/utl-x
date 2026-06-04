---
title: logType
description: "logType — UTL-X System function. Log the type of a value to stderr and pass the value through unchanged."
pageClass: stdlib-page
---

# logType

<p class="stdlib-meta"><code>logType(value) → value</code> · <a href="/reference/stdlib#system">System</a></p>

Log the type of a value to stderr and pass the value through unchanged.

- `value` (required): any value to inspect

``` utlx
{
  data: logType($input.field)            // logs "type: string" to stderr, passes value through
}
```

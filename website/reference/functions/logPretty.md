---
title: logPretty
description: "logPretty — UTL-X System function. Log a pretty-printed representation of a value to stderr and pass the"
pageClass: stdlib-page
---

# logPretty

<p class="stdlib-meta"><code>logPretty(value) → value</code> · <a href="/reference/stdlib#system">System</a></p>

Log a pretty-printed representation of a value to stderr and pass the
value through unchanged.

- `value` (required): any value to inspect

``` utlx
{
  result: logPretty($input.payload)      // logs formatted JSON to stderr, passes value through
}
```

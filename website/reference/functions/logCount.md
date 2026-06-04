---
title: logCount
description: "logCount — UTL-X System function. Log the count/length of a value to stderr and pass the value through"
pageClass: stdlib-page
---

# logCount

<p class="stdlib-meta"><code>logCount(value) → value</code> · <a href="/reference/stdlib#system">System</a></p>

Log the count/length of a value to stderr and pass the value through
unchanged. Useful for debugging pipelines.

- `value` (required): array, string, or object to count

``` utlx
{
  items: logCount($input.orders)         // logs "count: 42" to stderr, passes array through
}
```

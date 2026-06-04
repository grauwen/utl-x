---
title: debug
description: "debug — UTL-X System function. Log a value at DEBUG level and pass it through (does not consume the"
pageClass: stdlib-page
---

# debug

<p class="stdlib-meta"><code>debug(value, message?) → value</code> · <a href="/reference/stdlib#system">System</a></p>

Log a value at DEBUG level and pass it through (does not consume the
value).

- `value` (required): value to log and return

- `message` (optional): label for the log entry

``` utlx
{
  result: debug($input.amount, "processing amount")
}
// logs: [DEBUG] processing amount: 150.00
// output: {"result": 150.00}
```

---
title: logSize
description: "logSize — UTL-X System function. Log the byte size of a value to stderr and pass the value through"
pageClass: stdlib-page
---

# logSize

<p class="stdlib-meta"><code>logSize(value) → value</code> · <a href="/reference/stdlib#system">System</a></p>

Log the byte size of a value to stderr and pass the value through
unchanged.

- `value` (required): any value to measure

``` utlx
{
  output: logSize($input.document)       // logs "size: 4096 bytes" to stderr, passes value through
}
```

---
title: memoryInfo
description: "memoryInfo — UTL-X System function. Get JVM memory information in bytes. Returns an object with maxMemory,"
pageClass: stdlib-page
---

# memoryInfo

<p class="stdlib-meta"><code>memoryInfo() → object</code> · <a href="/reference/stdlib#system">System</a></p>

Get JVM memory information in bytes. Returns an object with `maxMemory`,
`totalMemory`, `freeMemory`, and `usedMemory`.

``` utlx
{
  mem: memoryInfo()
  // {maxMemory: 4294967296, totalMemory: 2147483648, freeMemory: 1073741824, usedMemory: 1073741824}
}
```

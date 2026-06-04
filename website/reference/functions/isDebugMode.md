---
title: isDebugMode
description: "isDebugMode — UTL-X System function. Check if the current transformation is running in debug mode."
pageClass: stdlib-page
---

# isDebugMode

<p class="stdlib-meta"><code>isDebugMode() → boolean</code> · <a href="/reference/stdlib#system">System</a></p>

Check if the current transformation is running in debug mode.

``` utlx
{
  debug: isDebugMode(),
  extra: if (isDebugMode()) { logs: getLogs() } else null
}
```

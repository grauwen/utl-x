---
title: setLogLevel
description: "setLogLevel — UTL-X System function. Set the minimum log level for output ('TRACE', 'DEBUG', 'INFO',"
pageClass: stdlib-page
---

# setLogLevel

<p class="stdlib-meta"><code>setLogLevel(level) → null</code> · <a href="/reference/stdlib#system">System</a></p>

Set the minimum log level for output (`"TRACE"`, `"DEBUG"`, `"INFO"`,
`"WARN"`, `"ERROR"`).

- `level` (required): log level string

``` utlx
let _ = setLogLevel("DEBUG")
```

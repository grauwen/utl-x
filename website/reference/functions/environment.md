---
title: environment
description: "environment — UTL-X System function. Get the current environment name (e.g., 'development', 'production')."
pageClass: stdlib-page
---

# environment

<p class="stdlib-meta"><code>environment() → string</code> · <a href="/reference/stdlib#system">System</a></p>

Get the current environment name (e.g., "development", "production").

``` utlx
{
  env: environment(),
  isProduction: environment() == "production"
}
```

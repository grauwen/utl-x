---
title: envOrDefault
description: "envOrDefault — UTL-X System function. Read an environment variable, returning a default value if not set."
pageClass: stdlib-page
---

# envOrDefault

<p class="stdlib-meta"><code>envOrDefault(name, default) → string</code> · <a href="/reference/stdlib#system">System</a></p>

Read an environment variable, returning a default value if not set.

- `name` (required): environment variable name

- `default` (required): fallback value if the variable is not set

``` utlx
envOrDefault("LOG_LEVEL", "INFO")        // "INFO" if LOG_LEVEL not set
{
  logLevel: envOrDefault("LOG_LEVEL", "INFO"),
  dbUrl: envOrDefault("DATABASE_URL", "postgres://localhost:5432/mydb")
}
```

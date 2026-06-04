---
title: hasEnv
description: "hasEnv — UTL-X System function. Check if an environment variable exists (is set, even if empty)."
pageClass: stdlib-page
---

# hasEnv

<p class="stdlib-meta"><code>hasEnv(name) → boolean</code> · <a href="/reference/stdlib#system">System</a></p>

Check if an environment variable exists (is set, even if empty).

- `name` (required): environment variable name

``` utlx
{
  hasDatabase: hasEnv("DATABASE_URL"),
  hasSecret: hasEnv("API_SECRET")
}
```

**Security note:** can be restricted in UTLXe via security policy
(Chapter 38) to prevent probing for host secrets.

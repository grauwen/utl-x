---
title: env
description: "env — UTL-X System function. Read an environment variable from the host system. Returns null if not"
pageClass: stdlib-page
---

# env

<p class="stdlib-meta"><code>env(name) → string</code> · <a href="/reference/stdlib#system">System</a></p>

Read an environment variable from the host system. Returns `null` if not
set.

- `name` (required): environment variable name

``` utlx
{
  home: env("HOME"),                     // "/Users/alice"
  dbHost: env("DB_HOST") ?? "localhost"  // fallback if not set
}
```

Also: `hasEnv(name)` → boolean, `envAll()` → object with all environment
variables.

**Security note:** `env()` is unrestricted in the CLI and IDE. In the
UTLXe engine, environment variable access can be disabled or restricted
via the security policy configuration (Chapter 38) to prevent
transformations from reading host secrets in multi-tenant deployments.

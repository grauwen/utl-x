---
title: envAll
description: "envAll — UTL-X System function. Get all environment variables as a key-value object."
pageClass: stdlib-page
---

# envAll

<p class="stdlib-meta"><code>envAll() → object</code> · <a href="/reference/stdlib#system">System</a></p>

Get all environment variables as a key-value object.

``` utlx
let allEnv = envAll()
{
  home: allEnv.HOME,
  path: allEnv.PATH,
  count: count(entries(allEnv))
}
```

**Security note:** can be restricted in UTLXe via security policy
(Chapter 38) to prevent exfiltration of host secrets in multi-tenant
deployments.

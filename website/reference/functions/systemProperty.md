---
title: systemProperty
description: "systemProperty — UTL-X System function. Get a Java system property by name. Returns null if not set."
pageClass: stdlib-page
---

# systemProperty

<p class="stdlib-meta"><code>systemProperty(name) → string</code> · <a href="/reference/stdlib#system">System</a></p>

Get a Java system property by name. Returns null if not set.

- `name` (required): property name (e.g. `"java.version"`)

``` utlx
{
  javaHome: systemProperty("java.home")
}
```

**Security note:** can be restricted in UTLXe via security policy
(Chapter 38).

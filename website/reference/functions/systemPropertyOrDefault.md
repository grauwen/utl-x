---
title: systemPropertyOrDefault
description: "systemPropertyOrDefault — UTL-X System function. Get a system property with a fallback default value."
pageClass: stdlib-page
---

# systemPropertyOrDefault

<p class="stdlib-meta"><code>systemPropertyOrDefault(name, default) → string</code> · <a href="/reference/stdlib#system">System</a></p>

Get a system property with a fallback default value.

- `name` (required): property name

- `default` (required): fallback value if property is not set

``` utlx
{
  encoding: systemPropertyOrDefault("file.encoding", "UTF-8")
}
```

**Security note:** can be restricted in UTLXe via security policy
(Chapter 38).

## T

---
title: systemPropertiesAll
description: "systemPropertiesAll — UTL-X System function. Get all Java system properties as an object."
pageClass: stdlib-page
---

# systemPropertiesAll

<p class="stdlib-meta"><code>systemPropertiesAll() → object</code> · <a href="/reference/stdlib#system">System</a></p>

Get all Java system properties as an object.

``` utlx
{
  allProps: systemPropertiesAll()
}
```

**Security note:** can be restricted in UTLXe via security policy
(Chapter 38) to prevent exfiltration of host configuration in
multi-tenant deployments.

---
title: containsValue
description: "containsValue — UTL-X Object function. Check if an object contains a specific value (searches all values)."
pageClass: stdlib-page
---

# containsValue

<p class="stdlib-meta"><code>containsValue(object, value) → boolean</code> · <a href="/reference/stdlib#object">Object</a></p>

Check if an object contains a specific value (searches all values).

- `object` (required): object to search

- `value` (required): value to find

``` utlx
let config = {host: "localhost", port: 5432, db: "mydb"}
{
  hasLocalhost: containsValue(config, "localhost"),   // true
  hasRedis: containsValue(config, "redis")           // false
}
```

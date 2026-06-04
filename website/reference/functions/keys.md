---
title: keys
description: "keys — UTL-X Object function. Get all property names (keys) from an object. Key order is preserved"
pageClass: stdlib-page
---

# keys

<p class="stdlib-meta"><code>keys(object) → array</code> · <a href="/reference/stdlib#object">Object</a></p>

Get all property names (keys) from an object. Key order is preserved
(insertion order).

- `object` (required): the object to inspect

``` bash
echo '{"name": "Alice", "age": 30, "city": "Amsterdam"}' | utlx -e 'keys($input)'
# ["name", "age", "city"]
```

``` utlx
// Iterate over dynamic keys
{
  servers: map(keys($input.servers), (env) -> {
    environment: env,
    host: $input.servers[env].host
  })
}
```

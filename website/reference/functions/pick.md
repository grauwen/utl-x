---
title: pick
description: "pick — UTL-X Object function. Return a new object WITH ONLY the listed properties."
pageClass: stdlib-page
---

# pick

<p class="stdlib-meta"><code>pick(object, keys) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Return a new object WITH ONLY the listed properties.

- `object` (required): the source object

- `keys` (required): array of property names to keep

``` bash
echo '{"name": "Alice", "email": "alice@example.com", "password": "secret", "role": "admin"}' | utlx -e 'pick($input, ["name", "email"])'
# {"name": "Alice", "email": "alice@example.com"}
```

---
title: omit
description: "omit — UTL-X Object function. Return a new object WITHOUT the listed properties."
pageClass: stdlib-page
---

# omit

<p class="stdlib-meta"><code>omit(object, keys) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Return a new object WITHOUT the listed properties.

- `object` (required): the source object

- `keys` (required): array of property names to exclude

``` bash
echo '{"name": "Alice", "email": "alice@example.com", "password": "secret", "role": "admin"}' | utlx -e 'omit($input, ["password"])'
# {"name": "Alice", "email": "alice@example.com", "role": "admin"}
```

``` utlx
let safe = omit($input, ["password", "apiKey", "token", "secret"])
{
  sanitized: safe
}
```

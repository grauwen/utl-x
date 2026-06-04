---
title: mapKeys
description: "mapKeys — UTL-X Object function. Transform the keys of an object, keeping values unchanged."
pageClass: stdlib-page
---

# mapKeys

<p class="stdlib-meta"><code>mapKeys(object, fn) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Transform the keys of an object, keeping values unchanged.

- `object` (required): the object to transform

- `fn` (required): lambda `(key) -> newKey`

``` bash
echo '{"first_name": "Alice", "last_name": "Johnson", "age": 30}' | utlx -e 'mapKeys($input, (key) -> camelCase(key))'
# {"firstName": "Alice", "lastName": "Johnson", "age": 30}
```

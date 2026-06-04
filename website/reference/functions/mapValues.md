---
title: mapValues
description: "mapValues — UTL-X Object function. Transform the values of an object, keeping keys unchanged."
pageClass: stdlib-page
---

# mapValues

<p class="stdlib-meta"><code>mapValues(object, fn) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Transform the values of an object, keeping keys unchanged.

- `object` (required): the object to transform

- `fn` (required): lambda `(value) -> newValue`

``` bash
echo '{"first_name": "Alice", "last_name": "Johnson", "age": 30}' | utlx -e 'mapValues($input, (value) -> toString(value))'
# {"first_name": "Alice", "last_name": "Johnson", "age": "30"}
```

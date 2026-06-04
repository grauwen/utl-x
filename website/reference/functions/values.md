---
title: values
description: "values — UTL-X Object function. Get all property values from an object. Order matches keys()."
pageClass: stdlib-page
---

# values

<p class="stdlib-meta"><code>values(object) → array</code> · <a href="/reference/stdlib#object">Object</a></p>

Get all property values from an object. Order matches `keys()`.

- `object` (required): the object to inspect

``` bash
echo '{"name": "Alice", "age": 30, "city": "Amsterdam"}' | utlx -e 'values($input)'
# ["Alice", 30, "Amsterdam"]
```

## L

---
title: yamlValues
description: "yamlValues — UTL-X YAML function. Get all values from a YAML object. See Chapter 26."
pageClass: stdlib-page
---

# yamlValues

<p class="stdlib-meta"><code>yamlValues(object) → array</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Get all values from a YAML object. See Chapter 26.

- `object` (required): YAML object

``` utlx
yamlValues({host: "localhost", port: 5432})
// ["localhost", 5432]
```

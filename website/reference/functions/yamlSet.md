---
title: yamlSet
description: "yamlSet — UTL-X YAML function. Return a new YAML structure with the value at the given path replaced."
pageClass: stdlib-page
---

# yamlSet

<p class="stdlib-meta"><code>yamlSet(yaml, path, value) → value</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Return a new YAML structure with the value at the given path replaced.
See Chapter 26.

- `yaml` (required): YAML UDM value

- `path` (required): dot-separated path string

- `value` (required): value to set at path

``` utlx
yamlSet($input, "database.port", 5433)   // returns new structure with port changed
```

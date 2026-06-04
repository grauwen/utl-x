---
title: yamlExists
description: "yamlExists — UTL-X YAML function. Check if a path exists in a YAML structure. See Chapter 26."
pageClass: stdlib-page
---

# yamlExists

<p class="stdlib-meta"><code>yamlExists(yaml, path) → boolean</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Check if a path exists in a YAML structure. See Chapter 26.

- `yaml` (required): YAML value

- `path` (required): dot-separated path

``` utlx
yamlExists($input, "database.host")      // true or false
```

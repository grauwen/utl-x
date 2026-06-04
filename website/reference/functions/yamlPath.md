---
title: yamlPath
description: "yamlPath — UTL-X YAML function. Access a nested value in a YAML structure using a dot-separated path."
pageClass: stdlib-page
---

# yamlPath

<p class="stdlib-meta"><code>yamlPath(yaml, path) → value</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Access a nested value in a YAML structure using a dot-separated path.
See Chapter 26.

- `yaml` (required): YAML UDM value

- `path` (required): dot-separated path string

``` utlx
yamlPath($input, "database.host")        // "localhost"
```

---
title: parseYaml
description: "parseYaml — UTL-X Format function. Parse a YAML string into a navigable UDM value. See Chapter 26."
pageClass: stdlib-page
---

# parseYaml

<p class="stdlib-meta"><code>parseYaml(string) → value</code> · <a href="/reference/stdlib#format">Format</a></p>

Parse a YAML string into a navigable UDM value. See Chapter 26.

- `string` (required): the YAML string to parse

``` utlx
let config = parseYaml($input.yamlConfig)
config.database.host                     // "localhost"
```

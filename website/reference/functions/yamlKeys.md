---
title: yamlKeys
description: "yamlKeys — UTL-X YAML function. Get all keys from a YAML object. See Chapter 26."
pageClass: stdlib-page
---

# yamlKeys

<p class="stdlib-meta"><code>yamlKeys(object) → array</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Get all keys from a YAML object. See Chapter 26.

- `object` (required): YAML object

``` utlx
yamlKeys($input)                         // ["apiVersion", "kind", "metadata", "spec"]
```

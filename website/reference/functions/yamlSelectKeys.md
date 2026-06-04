---
title: yamlSelectKeys
description: "yamlSelectKeys — UTL-X YAML function. Keep only specific keys from a YAML object. See Chapter 26."
pageClass: stdlib-page
---

# yamlSelectKeys

<p class="stdlib-meta"><code>yamlSelectKeys(object, keys) → object</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Keep only specific keys from a YAML object. See Chapter 26.

- `object` (required): YAML object

- `keys` (required): array of keys to keep

``` utlx
yamlSelectKeys($input, ["name", "version"])
```

---
title: yamlOmitKeys
description: "yamlOmitKeys — UTL-X YAML function. Remove specific keys from a YAML object. See Chapter 26."
pageClass: stdlib-page
---

# yamlOmitKeys

<p class="stdlib-meta"><code>yamlOmitKeys(object, keys) → object</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Remove specific keys from a YAML object. See Chapter 26.

- `object` (required): YAML object

- `keys` (required): array of keys to remove

``` utlx
yamlOmitKeys($input, ["password", "secret"])
```

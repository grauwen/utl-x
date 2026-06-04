---
title: yamlFindByField
description: "yamlFindByField — UTL-X YAML function. Find all values in a YAML structure by field name (recursive search)."
pageClass: stdlib-page
---

# yamlFindByField

<p class="stdlib-meta"><code>yamlFindByField(yaml, fieldName) → array</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Find all values in a YAML structure by field name (recursive search).
See Chapter 26.

- `yaml` (required): YAML value

- `fieldName` (required): field name to search for

``` utlx
yamlFindByField($input, "version")       // all "version" values in the tree
```

---
title: yamlDelete
description: "yamlDelete — UTL-X YAML function. Return a new YAML structure with the value at the given path removed."
pageClass: stdlib-page
---

# yamlDelete

<p class="stdlib-meta"><code>yamlDelete(yaml, path) → value</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Return a new YAML structure with the value at the given path removed.
See Chapter 26.

- `yaml` (required): YAML UDM value

- `path` (required): dot-separated path string

``` utlx
yamlDelete($input, "database.password")  // returns structure without password
```

Also: `yamlDeepMerge(obj1, obj2)`, `yamlKeys(obj)`, `yamlValues(obj)`,
`yamlSort(obj)`, `yamlValidate(yaml, rules)`,
`yamlFilterByKeyPattern(obj, pattern)`.

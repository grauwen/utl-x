---
title: yamlFilterByKeyPattern
description: "yamlFilterByKeyPattern — UTL-X YAML function. Filter a YAML object, keeping only keys that match a pattern. See"
pageClass: stdlib-page
---

# yamlFilterByKeyPattern

<p class="stdlib-meta"><code>yamlFilterByKeyPattern(object, pattern) → object</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Filter a YAML object, keeping only keys that match a pattern. See
Chapter 26.

- `object` (required): YAML object

- `pattern` (required): regex pattern for keys

``` utlx
yamlFilterByKeyPattern($input, "^db_.*") // keys starting with "db_"
```

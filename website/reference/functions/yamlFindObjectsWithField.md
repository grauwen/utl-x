---
title: yamlFindObjectsWithField
description: "yamlFindObjectsWithField — UTL-X YAML function. Find all objects containing a specific field (recursive search). See"
pageClass: stdlib-page
---

# yamlFindObjectsWithField

<p class="stdlib-meta"><code>yamlFindObjectsWithField(yaml, fieldName) → array</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Find all objects containing a specific field (recursive search). See
Chapter 26.

- `yaml` (required): YAML value

- `fieldName` (required): field name to search for

``` utlx
yamlFindObjectsWithField($input, "image")
// all objects that have an "image" field
```

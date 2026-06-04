---
title: yamlGetDocument
description: "yamlGetDocument — UTL-X YAML function. Get a specific document from a multi-document YAML by index. See Chapter"
pageClass: stdlib-page
---

# yamlGetDocument

<p class="stdlib-meta"><code>yamlGetDocument(yaml, index) → value</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Get a specific document from a multi-document YAML by index. See Chapter
26.

- `yaml` (required): multi-document YAML string

- `index` (required): zero-based document index

``` utlx
yamlGetDocument(multiDocYaml, 0)         // first document
```

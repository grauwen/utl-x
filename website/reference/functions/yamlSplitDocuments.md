---
title: yamlSplitDocuments
description: "yamlSplitDocuments — UTL-X YAML function. Split a multi-document YAML string (separated by ---) into an array of"
pageClass: stdlib-page
---

# yamlSplitDocuments

<p class="stdlib-meta"><code>yamlSplitDocuments(yaml) → array</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Split a multi-document YAML string (separated by `---`) into an array of
documents. See Chapter 26.

- `yaml` (required): YAML string containing multiple documents

``` utlx
let docs = yamlSplitDocuments(multiDocString)
docs[0]                                  // first document
docs[1]                                  // second document
```

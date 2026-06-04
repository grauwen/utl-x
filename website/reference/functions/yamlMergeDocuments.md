---
title: yamlMergeDocuments
description: "yamlMergeDocuments — UTL-X YAML function. Merge an array of YAML documents back into a single multi-document"
pageClass: stdlib-page
---

# yamlMergeDocuments

<p class="stdlib-meta"><code>yamlMergeDocuments(docs) → string</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Merge an array of YAML documents back into a single multi-document
string joined with `---` separators. See Chapter 26.

- `docs` (required): array of documents

``` utlx
yamlMergeDocuments(docs)                 // joined with --- separators
```

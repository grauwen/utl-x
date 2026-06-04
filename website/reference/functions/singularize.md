---
title: singularize
description: "singularize — UTL-X String function. Convert a plural noun to its singular form."
pageClass: stdlib-page
---

# singularize

<p class="stdlib-meta"><code>singularize(word) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a plural noun to its singular form.

- `word` (required): plural noun

``` utlx
singularize("children")                  // "child"
singularize("items")                     // "item"
singularize("categories")               // "category"
```

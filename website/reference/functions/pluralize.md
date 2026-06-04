---
title: pluralize
description: "pluralize — UTL-X String function. Convert a singular noun to its plural form."
pageClass: stdlib-page
---

# pluralize

<p class="stdlib-meta"><code>pluralize(word) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a singular noun to its plural form.

- `word` (required): singular noun

``` utlx
pluralize("child")                       // "children"
pluralize("item")                        // "items"
pluralize("category")                    // "categories"
```

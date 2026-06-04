---
title: charCodeAt
description: "charCodeAt — UTL-X String function. Get the Unicode code point of the character at a specific index."
pageClass: stdlib-page
---

# charCodeAt

<p class="stdlib-meta"><code>charCodeAt(string, index) → number</code> · <a href="/reference/stdlib#string">String</a></p>

Get the Unicode code point of the character at a specific index.

- `string` (required): the source string

- `index` (required): position (0-based)

``` utlx
charCodeAt("A", 0)    // 65
charCodeAt("a", 0)    // 97
charCodeAt("€", 0)    // 8364
```

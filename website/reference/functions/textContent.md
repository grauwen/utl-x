---
title: textContent
description: "textContent — UTL-X XML function. Get concatenated text content from an XML element (all text nodes). See"
pageClass: stdlib-page
---

# textContent

<p class="stdlib-meta"><code>textContent(element) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get concatenated text content from an XML element (all text nodes). See
Chapter 22.

- `element` (required): an XML element

``` utlx
// Input: <p>Hello <b>world</b>!</p>
textContent($input.p)                    // "Hello world!"
```

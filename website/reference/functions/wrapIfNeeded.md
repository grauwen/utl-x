---
title: wrapIfNeeded
description: "wrapIfNeeded — UTL-X XML function. Automatically wrap content in CDATA if it contains enough special"
pageClass: stdlib-page
---

# wrapIfNeeded

<p class="stdlib-meta"><code>wrapIfNeeded(content, threshold?, tag?) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Automatically wrap content in CDATA if it contains enough special
characters to benefit. See Chapter 22.

- `content` (required): the text content

- `threshold` (optional): special char count threshold

- `tag` (optional): wrapper format

``` utlx
wrapIfNeeded("<script>alert('hi')</script>")
// "<![CDATA[<script>alert('hi')</script>]]>"

wrapIfNeeded("plain text")              // "plain text" (no wrapping needed)
```

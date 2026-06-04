---
title: shouldUseCDATA
description: "shouldUseCDATA — UTL-X XML function. Determine if content should be wrapped in a CDATA section (based on"
pageClass: stdlib-page
---

# shouldUseCDATA

<p class="stdlib-meta"><code>shouldUseCDATA(content, threshold?) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Determine if content should be wrapped in a CDATA section (based on
special character count). See Chapter 22.

- `content` (required): the text content to evaluate

- `threshold` (optional): number of special chars that triggers CDATA

``` utlx
shouldUseCDATA("<script>alert('hi')</script>")  // true
shouldUseCDATA("plain text")                     // false
```

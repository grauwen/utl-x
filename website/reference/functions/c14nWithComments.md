---
title: c14nWithComments
description: "c14nWithComments — UTL-X XML function. Canonicalize XML using Canonical XML 1.0 with comments preserved."
pageClass: stdlib-page
---

# c14nWithComments

<p class="stdlib-meta"><code>c14nWithComments(xml) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Canonicalize XML using Canonical XML 1.0 with comments preserved.

- `xml` (required): XML UDM value

``` utlx
{
  canonical: c14nWithComments($input)
}
```

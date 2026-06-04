---
title: c14n11WithComments
description: "c14n11WithComments — UTL-X XML function. Canonicalize XML using Canonical XML 1.1 with comments preserved."
pageClass: stdlib-page
---

# c14n11WithComments

<p class="stdlib-meta"><code>c14n11WithComments(xml) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Canonicalize XML using Canonical XML 1.1 with comments preserved.

- `xml` (required): XML UDM value

``` utlx
{
  canonical: c14n11WithComments($input)
}
```

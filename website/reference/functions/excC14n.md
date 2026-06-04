---
title: excC14n
description: "excC14n — UTL-X XML function. Canonicalizes XML using Exclusive Canonical XML (without comments). Used"
pageClass: stdlib-page
---

# excC14n

<p class="stdlib-meta"><code>excC14n(xml) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Canonicalizes XML using Exclusive Canonical XML (without comments). Used
for XML digital signatures. See Chapter 22.

- `xml` (required): XML string to canonicalize

``` utlx
{
  canonical: excC14n($input.xml)
}
```

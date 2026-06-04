---
title: excC14nWithComments
description: "excC14nWithComments — UTL-X XML function. Canonicalizes XML using Exclusive Canonical XML (with comments"
pageClass: stdlib-page
---

# excC14nWithComments

<p class="stdlib-meta"><code>excC14nWithComments(xml) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Canonicalizes XML using Exclusive Canonical XML (with comments
preserved). See Chapter 22.

- `xml` (required): XML string to canonicalize

``` utlx
{
  canonical: excC14nWithComments($input.xml)
}
```

---
title: c14nSubset
description: "c14nSubset — UTL-X XML function. Canonicalize a subset of an XML document selected by XPath expression."
pageClass: stdlib-page
---

# c14nSubset

<p class="stdlib-meta"><code>c14nSubset(xml, xpath) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Canonicalize a subset of an XML document selected by XPath expression.

- `xml` (required): XML UDM value

- `xpath` (required): XPath expression selecting the subset to
  canonicalize

``` utlx
// See Chapter 22 for XML canonicalization.
{
  bodyCanonical: c14nSubset($input, "//soap:Body")
}
```

---
title: canonicalizeWithAlgorithm
description: "canonicalizeWithAlgorithm — UTL-X XML function. Canonicalize XML using a specified W3C canonicalization algorithm URI."
pageClass: stdlib-page
---

# canonicalizeWithAlgorithm

<p class="stdlib-meta"><code>canonicalizeWithAlgorithm(xml, algorithm) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Canonicalize XML using a specified W3C canonicalization algorithm URI.

- `xml` (required): XML UDM value

- `algorithm` (required): algorithm URI (e.g.,
  `"http://www.w3.org/2001/10/xml-exc-c14n#"`)

``` utlx
// See Chapter 22 for XML canonicalization.
{
  canonical: canonicalizeWithAlgorithm($input, "http://www.w3.org/2001/10/xml-exc-c14n#")
}
```

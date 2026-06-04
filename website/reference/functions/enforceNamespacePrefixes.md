---
title: enforceNamespacePrefixes
description: "enforceNamespacePrefixes — UTL-X XML function. Enforces specific namespace prefixes on an XML string, renaming prefixes"
pageClass: stdlib-page
---

# enforceNamespacePrefixes

<p class="stdlib-meta"><code>enforceNamespacePrefixes(xml, prefixMap) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Enforces specific namespace prefixes on an XML string, renaming prefixes
to match the given mapping. See Chapter 22.

- `xml` (required): XML string to process

- `prefixMap` (optional): object mapping namespace URIs to desired
  prefixes

``` utlx
{
  normalized: enforceNamespacePrefixes($input.xml)
}
```

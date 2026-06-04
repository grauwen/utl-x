---
title: elementPath
description: "elementPath — UTL-X XML function. Get the XPath-like path of an XML element within its document tree. See"
pageClass: stdlib-page
---

# elementPath

<p class="stdlib-meta"><code>elementPath(element) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get the XPath-like path of an XML element within its document tree. See
Chapter 22.

- `element` (required): XML UDM element

``` utlx
{
  path: elementPath($input.Invoice.Lines.Line),
  debug: elementPath($input.root.child)
}
```

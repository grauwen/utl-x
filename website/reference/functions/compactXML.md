---
title: compactXML
description: "compactXML — UTL-X XML function. Compact an XML string by removing unnecessary whitespace between"
pageClass: stdlib-page
---

# compactXML

<p class="stdlib-meta"><code>compactXML(xml, options?, indent?) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Compact an XML string by removing unnecessary whitespace between
elements.

- `xml` (required): XML string to compact

- `options` (optional): formatting options

- `indent` (optional): indentation level

``` utlx
// See Chapter 22 for XML processing.
{
  minified: compactXML(renderXml($input))
}
```

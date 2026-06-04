---
title: convertXMLEncoding
description: "convertXMLEncoding — UTL-X XML function. Convert an XML document to a different character encoding. See Chapter"
pageClass: stdlib-page
---

# convertXMLEncoding

<p class="stdlib-meta"><code>convertXMLEncoding(xml, targetEncoding) → binary</code> · <a href="/reference/stdlib#xml">XML</a></p>

Convert an XML document to a different character encoding. See Chapter
22.

- `xml` (required): XML string or UDM value

- `targetEncoding` (required): target encoding — `"UTF-8"`,
  `"ISO-8859-1"`, `"UTF-16"`, etc.

``` utlx
// See Chapter 22 for XML encoding handling.
{
  latin1Xml: convertXMLEncoding($input, "ISO-8859-1")
}
```

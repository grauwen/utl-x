---
title: updateXMLEncoding
description: "updateXMLEncoding — UTL-X XML function. Update the encoding declaration in an XML processing instruction. See"
pageClass: stdlib-page
---

# updateXMLEncoding

<p class="stdlib-meta"><code>updateXMLEncoding(xml, encoding) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Update the encoding declaration in an XML processing instruction. See
Chapter 22.

- `xml` (required): XML string

- `encoding` (required): new encoding name (e.g. `"UTF-16"`)

``` utlx
updateXMLEncoding($input.xmlData, "UTF-8")
```

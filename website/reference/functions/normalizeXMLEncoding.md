---
title: normalizeXMLEncoding
description: "normalizeXMLEncoding — UTL-X XML function. Auto-detect the encoding of an XML string and convert it to a target"
pageClass: stdlib-page
---

# normalizeXMLEncoding

<p class="stdlib-meta"><code>normalizeXMLEncoding(xml, targetEncoding) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Auto-detect the encoding of an XML string and convert it to a target
encoding. Also updates the XML declaration. See Chapter 22.

- `xml` (required): XML string to re-encode

- `targetEncoding` (required): target encoding (e.g., `"UTF-8"`)

``` utlx
{
  normalized: normalizeXMLEncoding($input.xmlPayload, "UTF-8")
}
```

---
title: detectXMLEncoding
description: "detectXMLEncoding — UTL-X XML function. Detect the encoding declared in an XML document's declaration."
pageClass: stdlib-page
---

# detectXMLEncoding

<p class="stdlib-meta"><code>detectXMLEncoding(xmlString) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Detect the encoding declared in an XML document's declaration.

- `xmlString` (required): XML string or UDM value

``` utlx
// Input: <?xml version="1.0" encoding="ISO-8859-1"?><Order>...</Order>
// See Chapter 22 for XML processing.
{
  encoding: detectXMLEncoding($input)
}
// Output: {"encoding": "ISO-8859-1"}

// Input: <Order>...</Order>  (no encoding declaration — defaults to UTF-8)
{
  encoding: detectXMLEncoding($input)
}
// Output: {"encoding": "UTF-8"}
```

Also: `convertXMLEncoding(xml, targetEncoding)` re-encodes the document.

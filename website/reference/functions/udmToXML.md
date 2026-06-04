---
title: udmToXML
description: "udmToXML — UTL-X Format function. Pretty-print a UDM object as XML."
pageClass: stdlib-page
---

# udmToXML

<p class="stdlib-meta"><code>udmToXML(value, rootName?, options?) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Pretty-print a UDM object as XML.

- `value` (required): UDM value to format

- `rootName` (optional): root element name

- `options` (optional): formatting options

``` utlx
udmToXML($input, "Order")               // XML string with <Order> root
```

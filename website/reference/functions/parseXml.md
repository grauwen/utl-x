---
title: parseXml
description: "parseXml — UTL-X Format function. Parse an XML string into a navigable UDM value. See Chapter 22."
pageClass: stdlib-page
---

# parseXml

<p class="stdlib-meta"><code>parseXml(string) → value</code> · <a href="/reference/stdlib#format">Format</a></p>

Parse an XML string into a navigable UDM value. See Chapter 22.

- `string` (required): the XML string to parse

``` utlx
let doc = parseXml($input.xmlPayload)
doc.Order.Customer                       // "Acme Corp"
```

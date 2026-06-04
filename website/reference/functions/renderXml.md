---
title: renderXml
description: "renderXml — UTL-X XML function. Render a UDM object as an XML string. See Chapter 22."
pageClass: stdlib-page
---

# renderXml

<p class="stdlib-meta"><code>renderXml(value) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Render a UDM object as an XML string. See Chapter 22.

- `value` (required): UDM value to serialize

``` utlx
renderXml({Order: {Id: "1", Item: "Widget"}})
// "<Order><Id>1</Id><Item>Widget</Item></Order>"
```

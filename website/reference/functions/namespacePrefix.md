---
title: namespacePrefix
description: "namespacePrefix — UTL-X XML function. Extract the namespace prefix from an XML element. See Chapter 22."
pageClass: stdlib-page
---

# namespacePrefix

<p class="stdlib-meta"><code>namespacePrefix(element) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Extract the namespace prefix from an XML element. See Chapter 22.

- `element` (required): XML UDM element

``` utlx
{
  prefix: namespacePrefix($input)            // "cbc"
}
```

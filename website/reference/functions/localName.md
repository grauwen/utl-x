---
title: localName
description: "localName — UTL-X XML function. Extract the local name (without prefix) from an XML qualified name. See"
pageClass: stdlib-page
---

# localName

<p class="stdlib-meta"><code>localName(element) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Extract the local name (without prefix) from an XML qualified name. See
Chapter 22.

- `element` (required): XML UDM element

``` utlx
{
  local: localName($input)                   // "InvoiceTypeCode"
}
```

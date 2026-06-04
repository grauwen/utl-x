---
title: nodeType
description: "nodeType — UTL-X XML function. Get the node type of an XML UDM node (element, attribute, text, etc.)."
pageClass: stdlib-page
---

# nodeType

<p class="stdlib-meta"><code>nodeType(node) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get the node type of an XML UDM node (element, attribute, text, etc.).
See Chapter 22.

- `node` (required): XML UDM node

``` utlx
{
  type: nodeType($input)                     // "element"
}
```

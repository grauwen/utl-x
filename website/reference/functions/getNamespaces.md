---
title: getNamespaces
description: "getNamespaces — UTL-X XML function. Get all namespace declarations from an XML element as a prefix-to-URI"
pageClass: stdlib-page
---

# getNamespaces

<p class="stdlib-meta"><code>getNamespaces(element) → object</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get all namespace declarations from an XML element as a prefix-to-URI
map. See Chapter 22.

- `element` (required): XML UDM element

``` utlx
let ns = getNamespaces($input.Invoice)
{
  namespaces: ns,
  isSoap: hasKey(ns, "soap"),
  hasCommonBasic: hasKey(ns, "cbc")
}
```

---
title: hasNamespace
description: "hasNamespace — UTL-X XML function. Check if an XML element has a specific namespace URI declared. See"
pageClass: stdlib-page
---

# hasNamespace

<p class="stdlib-meta"><code>hasNamespace(element, uri) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Check if an XML element has a specific namespace URI declared. See
Chapter 22.

- `element` (required): XML UDM element

- `uri` (required): namespace URI to check

``` utlx
{
  hasSoap: hasNamespace($input.root, "http://schemas.xmlsoap.org/soap/envelope/"),
  hasUBL: hasNamespace($input.Invoice, "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2")
}
```

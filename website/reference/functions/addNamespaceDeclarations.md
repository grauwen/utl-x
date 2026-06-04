---
title: addNamespaceDeclarations
description: "addNamespaceDeclarations — UTL-X XML function. Add namespace declarations to an XML element."
pageClass: stdlib-page
---

# addNamespaceDeclarations

<p class="stdlib-meta"><code>addNamespaceDeclarations(xml, namespaces) → xml</code> · <a href="/reference/stdlib#xml">XML</a></p>

Add namespace declarations to an XML element.

- `xml` (required): XML UDM value

- `namespaces` (required): object mapping prefix to URI —
  `{"cbc": "urn:...", "cac": "urn:..."}`

``` utlx
// See Chapter 22 for XML namespace handling.
{
  result: addNamespaceDeclarations($input, {
    "cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    "cac": "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
  })
}
```

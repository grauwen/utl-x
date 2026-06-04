---
title: createQname
description: "createQname — UTL-X XML function. Create a structured QName from its parts. See Chapter 22."
pageClass: stdlib-page
---

# createQname

<p class="stdlib-meta"><code>createQname(localName, namespaceUri, prefix?) → object</code> · <a href="/reference/stdlib#xml">XML</a></p>

Create a structured QName from its parts. See Chapter 22.

- `localName` (required): the local element name without prefix

- `namespaceUri` (required): the full namespace URI

- `prefix` (optional): the namespace prefix

``` utlx
// See Chapter 22 for XML namespaces.
{
  qname: createQname("Invoice",
    "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2", "cbc")
}
// Output: {qname: {localName: "Invoice",
//   namespaceUri: "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
//   prefix: "cbc", qualifiedName: "cbc:Invoice"}}

// Without prefix:
{
  qname: createQname("Order", "urn:example:orders")
}
// Output: {qname: {localName: "Order",
//   namespaceUri: "urn:example:orders",
//   prefix: "", qualifiedName: "Order"}}
```

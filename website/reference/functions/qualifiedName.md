---
title: qualifiedName
description: "qualifiedName — UTL-X XML function. Get the full qualified name (prefix:localName) of an XML element. See"
pageClass: stdlib-page
---

# qualifiedName

<p class="stdlib-meta"><code>qualifiedName(element) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get the full qualified name (prefix:localName) of an XML element. See
Chapter 22.

- `element` (required): XML UDM element

``` utlx
{
  qname: qualifiedName($input)               // "cbc:InvoiceTypeCode"
}
```

Also: `resolveQname(string, context)`, `matchesQname(element, pattern)`,
`hasNamespace(element)`.

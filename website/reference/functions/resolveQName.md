---
title: resolveQName
description: "resolveQName — UTL-X XML function. Resolve a QName string to a full qualified name with namespace URI. See"
pageClass: stdlib-page
---

# resolveQName

<p class="stdlib-meta"><code>resolveQName(qname, namespaces) → object</code> · <a href="/reference/stdlib#xml">XML</a></p>

Resolve a QName string to a full qualified name with namespace URI. See
Chapter 22.

- `qname` (required): qualified name (e.g. `"ns:Element"`)

- `namespaces` (required): namespace map object

``` utlx
resolveQName("soap:Envelope", {"soap": "http://schemas.xmlsoap.org/soap/envelope/"})
// {"localName": "Envelope",
//  "namespaceUri": "http://schemas.xmlsoap.org/soap/envelope/",
//  "prefix": "soap"}
```

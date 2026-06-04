---
title: namespaceUri
description: "namespaceUri — UTL-X XML function. Extract the namespace URI from an XML element. See Chapter 22."
pageClass: stdlib-page
---

# namespaceUri

<p class="stdlib-meta"><code>namespaceUri(element) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Extract the namespace URI from an XML element. See Chapter 22.

- `element` (required): XML UDM element

``` utlx
{
  ns: namespaceUri($input)                   // "urn:oasis:names:specification:ubl:..."
}
```

``` utlx
// Use case: filter XML elements by namespace (XBRL taxonomy)
let usgaapFacts = filter($input.*, (elem) ->
  namespaceUri(elem) == "http://fasb.org/us-gaap/2024"
)
{
  facts: usgaapFacts
}
```

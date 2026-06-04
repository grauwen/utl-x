---
title: attributes
description: "attributes — UTL-X XML function. Get all attributes from an XML element as a key-value object. Useful for"
pageClass: stdlib-page
---

# attributes

<p class="stdlib-meta"><code>attributes(element) → object</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get all attributes from an XML element as a key-value object. Useful for
iterating or spreading all attributes. For accessing individual
attributes, prefer `$input.@name`.

- `element` (required): XML UDM element

``` utlx
// Input: <Product id="P-1" sku="ABC123" category="electronics"/>
// See Chapter 22 for XML attribute access.
{
  allAttrs: attributes($input),         // {"id": "P-1", "sku": "ABC123", "category": "electronics"}
  attrCount: count(keys(attributes($input)))  // 3
}
```

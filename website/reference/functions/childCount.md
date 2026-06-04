---
title: childCount
description: "childCount — UTL-X XML function. Count the number of child elements in an XML element."
pageClass: stdlib-page
---

# childCount

<p class="stdlib-meta"><code>childCount(element) → number</code> · <a href="/reference/stdlib#xml">XML</a></p>

Count the number of child elements in an XML element.

- `element` (required): XML UDM element

``` utlx
// Input: <Order><Item/><Item/><Item/></Order>
// See Chapter 22 for XML processing.
{
  itemCount: childCount($input)    // 3
}
```

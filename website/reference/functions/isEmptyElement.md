---
title: isEmptyElement
description: "isEmptyElement — UTL-X XML function. Check if an XML element is empty (no children, no text content). See"
pageClass: stdlib-page
---

# isEmptyElement

<p class="stdlib-meta"><code>isEmptyElement(element) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Check if an XML element is empty (no children, no text content). See
Chapter 22.

- `element` (required): XML UDM element

``` utlx
{
  isEmpty: isEmptyElement($input.node),
  nonEmpty: filter($input.elements, (e) -> !isEmptyElement(e))
}
```

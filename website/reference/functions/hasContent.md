---
title: hasContent
description: "hasContent — UTL-X XML function. Check if an XML element has any content (child elements or text). See"
pageClass: stdlib-page
---

# hasContent

<p class="stdlib-meta"><code>hasContent(element) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Check if an XML element has any content (child elements or text). See
Chapter 22.

- `element` (required): XML UDM element

``` utlx
{
  hasBody: hasContent($input.element),
  emptyNodes: filter($input.nodes, (n) -> !hasContent(n))
}
```

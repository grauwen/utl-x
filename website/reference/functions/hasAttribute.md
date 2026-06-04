---
title: hasAttribute
description: "hasAttribute — UTL-X XML function. Check if an XML element has a specific attribute. See Chapter 22."
pageClass: stdlib-page
---

# hasAttribute

<p class="stdlib-meta"><code>hasAttribute(element, name) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Check if an XML element has a specific attribute. See Chapter 22.

- `element` (required): XML UDM element

- `name` (required): attribute name to check

``` utlx
{
  hasId: hasAttribute($input.element, "id"),
  hasClass: hasAttribute($input.element, "class")
}
```

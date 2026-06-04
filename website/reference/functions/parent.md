---
title: parent
description: "parent — UTL-X XML function. Get the parent element of an XML node (if metadata is available). See"
pageClass: stdlib-page
---

# parent

<p class="stdlib-meta"><code>parent(element) → element</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get the parent element of an XML node (if metadata is available). See
Chapter 22.

- `element` (required): an XML element

``` utlx
let p = parent($input.Order.LineItem)
p.OrderId                                // access sibling of parent
```

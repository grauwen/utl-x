---
title: formatEmptyElements
description: "formatEmptyElements — UTL-X XML function. Formats empty XML elements according to the specified style"
pageClass: stdlib-page
---

# formatEmptyElements

<p class="stdlib-meta"><code>formatEmptyElements(xml, style?) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Formats empty XML elements according to the specified style
(self-closing `<br/>` or expanded `<br></br>`). See Chapter 22.

- `xml` (required): XML string

- `style` (optional): "self-closing" or "expanded"

``` utlx
{
  selfClosing: formatEmptyElements($input.xml, "self-closing"),
  expanded: formatEmptyElements($input.xml, "expanded")
}
```

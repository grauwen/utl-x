---
title: xmlEscape
description: "xmlEscape — UTL-X XML function. Escape XML special characters (<, >, &, ', '). See Chapter 22."
pageClass: stdlib-page
---

# xmlEscape

<p class="stdlib-meta"><code>xmlEscape(string) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Escape XML special characters (`<`, `>`, `&`, `"`, `'`). See Chapter 22.

- `string` (required): the string to escape

``` utlx
xmlEscape("price < 100 & tax > 0")         // "price &lt; 100 &amp; tax &gt; 0"
```

``` utlx
{
  Comment: xmlEscape($input.userComment)
}
```

---
title: xmlUnescape
description: "xmlUnescape — UTL-X XML function. Unescape XML entity references back to their original characters. See"
pageClass: stdlib-page
---

# xmlUnescape

<p class="stdlib-meta"><code>xmlUnescape(string) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Unescape XML entity references back to their original characters. See
Chapter 22.

- `string` (required): the string to unescape

``` utlx
xmlUnescape("price &lt; 100 &amp; tax &gt; 0")  // "price < 100 & tax > 0"
```

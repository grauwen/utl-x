---
title: escapeXML
description: "escapeXML — UTL-X XML function. Escapes special XML characters (<, >, &, ', ') in text without"
pageClass: stdlib-page
---

# escapeXML

<p class="stdlib-meta"><code>escapeXML(text) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Escapes special XML characters (`<`, `>`, `&`, `"`, `'`) in text without
using CDATA. See Chapter 22.

- `text` (required): string to escape

``` bash
echo '{"text": "price < 100 & qty > 0"}' | utlx -e 'escapeXML($input.text)'
# "price &lt; 100 &amp; qty &gt; 0"
```

``` utlx
{
  safeText: escapeXML($input.userInput)
}
```

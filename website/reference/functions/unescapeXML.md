---
title: unescapeXML
description: "unescapeXML — UTL-X XML function. Unescape XML entities (&lt;, &gt;, &amp;, &quot;, &apos;) back"
pageClass: stdlib-page
---

# unescapeXML

<p class="stdlib-meta"><code>unescapeXML(string) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Unescape XML entities (`&lt;`, `&gt;`, `&amp;`, `&quot;`, `&apos;`) back
to their characters. See Chapter 22.

- `string` (required): string with XML entities

``` utlx
unescapeXML("price &lt; 100 &amp; qty &gt; 0")
// "price < 100 & qty > 0"
```

---
title: isCDATA
description: "isCDATA — UTL-X XML function. Checks if a string is a CDATA section (wrapped in <![CDATA[...]]>)."
pageClass: stdlib-page
---

# isCDATA

<p class="stdlib-meta"><code>isCDATA(text) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Checks if a string is a CDATA section (wrapped in `<![CDATA[...]]>`).
See Chapter 22.

- `text` (required): string to check

``` utlx
{
  isCdata: isCDATA($input.xmlField),
  content: if (isCDATA($input.field)) extractCDATA($input.field) else $input.field
}
```

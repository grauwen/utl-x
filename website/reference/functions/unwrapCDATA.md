---
title: unwrapCDATA
description: "unwrapCDATA — UTL-X XML function. Unwrap CDATA section if present, otherwise return the original string."
pageClass: stdlib-page
---

# unwrapCDATA

<p class="stdlib-meta"><code>unwrapCDATA(string) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Unwrap CDATA section if present, otherwise return the original string.
See Chapter 22.

- `string` (required): string that may be wrapped in `<![CDATA[...]]>`

``` utlx
unwrapCDATA("<![CDATA[Hello <World>]]>") // "Hello <World>"
unwrapCDATA("plain text")               // "plain text"
```

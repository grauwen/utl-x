---
title: extractCDATA
description: "extractCDATA — UTL-X XML function. Extracts the content from a CDATA section, stripping the <![CDATA[ and"
pageClass: stdlib-page
---

# extractCDATA

<p class="stdlib-meta"><code>extractCDATA(text) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Extracts the content from a CDATA section, stripping the `<![CDATA[` and
`]]>` wrappers. See Chapter 22.

- `text` (required): string containing CDATA section

``` bash
echo '{"xml": "<![CDATA[Hello & World]]>"}' | utlx -e 'extractCDATA($input.xml)'
# "Hello & World"
```

``` utlx
{
  content: extractCDATA($input.rawField)
}
```

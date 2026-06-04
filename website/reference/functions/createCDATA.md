---
title: createCDATA
description: "createCDATA — UTL-X XML function. Create a CDATA section wrapping the given content."
pageClass: stdlib-page
---

# createCDATA

<p class="stdlib-meta"><code>createCDATA(content) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Create a CDATA section wrapping the given content.

- `content` (required): text content to wrap

``` utlx
// See Chapter 22 for XML processing.
{
  wrapped: createCDATA("<script>alert('hi')</script>")
}
// Output: {"wrapped": "<![CDATA[<script>alert('hi')</script>]]>"}
```

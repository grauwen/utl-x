---
title: urlDecode
description: "urlDecode — UTL-X URL function. Decode a URL-encoded string (percent-encoding per RFC 3986)."
pageClass: stdlib-page
---

# urlDecode

<p class="stdlib-meta"><code>urlDecode(string) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Decode a URL-encoded string (percent-encoding per RFC 3986).

- `string` (required): the string to decode

``` utlx
urlDecode("hello%20world")               // "hello world"
```

Also: `urlDecodeComponent(string)`.

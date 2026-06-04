---
title: urlDecodeComponent
description: "urlDecodeComponent — UTL-X URL function. URL-decode a component string (RFC 3986) — decodes %20 as spaces."
pageClass: stdlib-page
---

# urlDecodeComponent

<p class="stdlib-meta"><code>urlDecodeComponent(string) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

URL-decode a component string (RFC 3986) — decodes `%20` as spaces.

- `string` (required): the encoded string

``` utlx
urlDecodeComponent("hello%20world")      // "hello world"
```

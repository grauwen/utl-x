---
title: urlEncodeComponent
description: "urlEncodeComponent — UTL-X URL function. URL-encode a component string (RFC 3986) — encodes spaces as %20 for"
pageClass: stdlib-page
---

# urlEncodeComponent

<p class="stdlib-meta"><code>urlEncodeComponent(string) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

URL-encode a component string (RFC 3986) — encodes spaces as `%20` for
URI paths.

- `string` (required): the string to encode

``` utlx
urlEncodeComponent("hello world/path")   // "hello%20world%2Fpath"
```

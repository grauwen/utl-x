---
title: removeQueryParam
description: "removeQueryParam — UTL-X URL function. Remove a query parameter from a URL string."
pageClass: stdlib-page
---

# removeQueryParam

<p class="stdlib-meta"><code>removeQueryParam(url, param) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Remove a query parameter from a URL string.

- `url` (required): the URL string

- `param` (required): parameter name to remove

``` utlx
removeQueryParam("https://example.com?page=1&size=10", "page")
// "https://example.com?size=10"
```

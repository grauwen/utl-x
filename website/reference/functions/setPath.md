---
title: setPath
description: "setPath — UTL-X URL function. Set or replace the path component of a URL."
pageClass: stdlib-page
---

# setPath

<p class="stdlib-meta"><code>setPath(url, path) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Set or replace the path component of a URL.

- `url` (required): the URL string

- `path` (required): new path to set

``` utlx
setPath("https://example.com/old/path", "/api/v2/resource")
// "https://example.com/api/v2/resource"
```

---
title: getPath
description: "getPath — UTL-X URL function. Get the path component from a URL string."
pageClass: stdlib-page
---

# getPath

<p class="stdlib-meta"><code>getPath(url) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Get the path component from a URL string.

- `url` (required): URL string

``` bash
echo '{"url": "https://api.example.com/v1/users/123"}' | utlx -e 'getPath($input.url)'
# "/v1/users/123"
```

``` utlx
{
  path: getPath($input.endpoint)
}
```

---
title: getHost
description: "getHost — UTL-X URL function. Get the host from a URL string."
pageClass: stdlib-page
---

# getHost

<p class="stdlib-meta"><code>getHost(url) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Get the host from a URL string.

- `url` (required): URL string

``` bash
echo '{"url": "https://api.example.com/v1/users"}' | utlx -e 'getHost($input.url)'
# "api.example.com"
```

``` utlx
{
  host: getHost($input.endpoint)
}
```

---
title: getPort
description: "getPort — UTL-X URL function. Get the port number from a URL string."
pageClass: stdlib-page
---

# getPort

<p class="stdlib-meta"><code>getPort(url) → number</code> · <a href="/reference/stdlib#url">URL</a></p>

Get the port number from a URL string.

- `url` (required): URL string

``` bash
echo '{"url": "https://api.example.com:8443/v1"}' | utlx -e 'getPort($input.url)'
# 8443
```

``` utlx
{
  port: getPort($input.url)
}
```

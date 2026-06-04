---
title: getBaseURL
description: "getBaseURL — UTL-X URL function. Get the base URL (protocol + host + port) from a URL string."
pageClass: stdlib-page
---

# getBaseURL

<p class="stdlib-meta"><code>getBaseURL(url) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Get the base URL (protocol + host + port) from a URL string.

- `url` (required): URL string

``` bash
echo '{"url": "https://api.example.com:8080/v1/users?page=1"}' | utlx -e 'getBaseURL($input.url)'
# "https://api.example.com:8080"
```

``` utlx
{
  base: getBaseURL($input.endpoint)
}
```

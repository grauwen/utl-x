---
title: getQueryParams
description: "getQueryParams — UTL-X URL function. Get query parameters from a URL as a key-value object."
pageClass: stdlib-page
---

# getQueryParams

<p class="stdlib-meta"><code>getQueryParams(url) → object</code> · <a href="/reference/stdlib#url">URL</a></p>

Get query parameters from a URL as a key-value object.

- `url` (required): URL string

``` bash
echo '{"url": "https://example.com/search?q=hello&page=2"}' | utlx -e 'getQueryParams($input.url)'
# {"q": "hello", "page": "2"}
```

``` utlx
{
  params: getQueryParams($input.url),
  searchTerm: getQueryParams($input.url).q
}
```

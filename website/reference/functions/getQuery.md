---
title: getQuery
description: "getQuery — UTL-X URL function. Get the query string from a URL (without the leading ?)."
pageClass: stdlib-page
---

# getQuery

<p class="stdlib-meta"><code>getQuery(url) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Get the query string from a URL (without the leading `?`).

- `url` (required): URL string

``` bash
echo '{"url": "https://example.com/search?q=hello&page=2"}' | utlx -e 'getQuery($input.url)'
# "q=hello&page=2"
```

``` utlx
{
  queryString: getQuery($input.url)
}
```

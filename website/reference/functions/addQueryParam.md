---
title: addQueryParam
description: "addQueryParam — UTL-X URL function. Add a query parameter to a URL string."
pageClass: stdlib-page
---

# addQueryParam

<p class="stdlib-meta"><code>addQueryParam(url, name, value) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Add a query parameter to a URL string.

- `url` (required): the base URL

- `name` (required): parameter name

- `value` (required): parameter value

``` bash
echo '{"baseUrl": "https://api.example.com/search", "term": "hello world"}' \
  | utlx -e 'addQueryParam($input.baseUrl, "q", $input.term)'
# "https://api.example.com/search?q=hello+world"
```

``` utlx
{
  url: addQueryParam(addQueryParam($input.baseUrl, "page", "1"), "limit", "50")
}
```

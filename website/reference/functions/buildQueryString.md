---
title: buildQueryString
description: "buildQueryString — UTL-X URL function. Build a URL query string from an object of key-value pairs."
pageClass: stdlib-page
---

# buildQueryString

<p class="stdlib-meta"><code>buildQueryString(params) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Build a URL query string from an object of key-value pairs.

- `params` (required): object with parameter names and values

``` bash
echo '{"page": 2, "limit": 50, "sort": "name"}' \
  | utlx -e 'buildQueryString($input)'
# "page=2&limit=50&sort=name"
```

``` utlx
{
  queryString: buildQueryString({q: $input.search, page: "1", format: "json"})
}
```

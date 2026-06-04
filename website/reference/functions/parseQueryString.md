---
title: parseQueryString
description: "parseQueryString — UTL-X URL function. Parse a query string into an object of key-value pairs."
pageClass: stdlib-page
---

# parseQueryString

<p class="stdlib-meta"><code>parseQueryString(string) → object</code> · <a href="/reference/stdlib#url">URL</a></p>

Parse a query string into an object of key-value pairs.

- `string` (required): query string (with or without leading `?`)

``` bash
echo '{"qs": "name=Alice&age=30&city=Amsterdam"}' | utlx -e 'parseQueryString($input.qs)'
# {"name": "Alice", "age": "30", "city": "Amsterdam"}
```

``` utlx
let params = parseQueryString($input.queryString)
{
  userName: params.name
}
```

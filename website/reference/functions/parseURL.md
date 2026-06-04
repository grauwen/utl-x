---
title: parseURL
description: "parseURL — UTL-X URL function. Parse a URL string into its components (protocol, host, port, path,"
pageClass: stdlib-page
---

# parseURL

<p class="stdlib-meta"><code>parseURL(url) → object</code> · <a href="/reference/stdlib#url">URL</a></p>

Parse a URL string into its components (protocol, host, port, path,
query, fragment).

- `url` (required): the URL string to parse

``` bash
echo '{"url": "https://example.com:8080/api/v1?key=abc#section"}' | utlx -e 'parseURL($input.url)'
# {"protocol": "https", "host": "example.com", "port": 8080,
#  "path": "/api/v1", "query": "key=abc", "fragment": "section"}
```

``` utlx
let parts = parseURL($input.endpoint)
{
  host: parts.host,
  isSecure: parts.protocol == "https"
}
```

---
title: buildURL
description: "buildURL — UTL-X URL function. Build a complete URL from its component parts."
pageClass: stdlib-page
---

# buildURL

<p class="stdlib-meta"><code>buildURL(components) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Build a complete URL from its component parts.

- `components` (required): object with `protocol`, `host`, `port?`,
  `path?`, `query?`, `fragment?`

``` bash
echo '{"host": "api.example.com", "path": "/v2/users"}' \
  | utlx -e 'buildURL({protocol: "https", host: $input.host, path: $input.path, query: {active: "true"}})'
# "https://api.example.com/v2/users?active=true"
```

``` utlx
{
  endpoint: buildURL({
    protocol: "https",
    host: $input.apiHost,
    path: concat("/api/v1/", $input.resource),
    query: {format: "json"}
  })
}
```

## C

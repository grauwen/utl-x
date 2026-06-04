---
title: urlEncode
description: "urlEncode — UTL-X URL function. URL-encode a string (percent-encoding per RFC 3986)."
pageClass: stdlib-page
---

# urlEncode

<p class="stdlib-meta"><code>urlEncode(string) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

URL-encode a string (percent-encoding per RFC 3986).

- `string` (required): the string to encode

``` utlx
urlEncode("hello world")                 // "hello%20world"
urlEncode("price=10&currency=EUR")       // "price%3D10%26currency%3DEUR"

// Use case: build a query string
let qs = join(map(entries($input.params), (e) ->
  concat(urlEncode(e[0]), "=", urlEncode(toString(e[1])))
), "&")
// Or use the dedicated function:
buildQueryString($input.params)
```

Also: `urlEncodeComponent(string)`, `buildURL(base, path, params)`,
`parseURL(url)`, `getHost(url)`, `getPath(url)`, `getQuery(url)`,
`getQueryParams(url)`.

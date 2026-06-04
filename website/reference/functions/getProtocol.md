---
title: getProtocol
description: "getProtocol — UTL-X URL function. Get the protocol/scheme from a URL string."
pageClass: stdlib-page
---

# getProtocol

<p class="stdlib-meta"><code>getProtocol(url) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Get the protocol/scheme from a URL string.

- `url` (required): URL string

``` bash
echo '{"url": "https://example.com"}' | utlx -e 'getProtocol($input.url)'
# "https"
```

``` utlx
{
  protocol: getProtocol($input.url),
  isSecure: getProtocol($input.url) == "https"
}
```

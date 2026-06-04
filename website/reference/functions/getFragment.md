---
title: getFragment
description: "getFragment — UTL-X URL function. Get the fragment (hash) portion from a URL string."
pageClass: stdlib-page
---

# getFragment

<p class="stdlib-meta"><code>getFragment(url) → string</code> · <a href="/reference/stdlib#url">URL</a></p>

Get the fragment (hash) portion from a URL string.

- `url` (required): URL string

``` bash
echo '{"url": "https://example.com/page#section2"}' | utlx -e 'getFragment($input.url)'
# "section2"
```

``` utlx
{
  fragment: getFragment($input.url)
}
```

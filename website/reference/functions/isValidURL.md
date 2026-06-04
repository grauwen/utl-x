---
title: isValidURL
description: "isValidURL — UTL-X URL function. Validate if a string is a well-formed URL."
pageClass: stdlib-page
---

# isValidURL

<p class="stdlib-meta"><code>isValidURL(url) → boolean</code> · <a href="/reference/stdlib#url">URL</a></p>

Validate if a string is a well-formed URL.

- `url` (required): string to validate

``` bash
echo '{"url": "https://example.com/path"}' | utlx -e 'isValidURL($input.url)'
# true
```

``` utlx
{
  valid: isValidURL($input.website),
  links: filter($input.urls, (u) -> isValidURL(u))
}
```

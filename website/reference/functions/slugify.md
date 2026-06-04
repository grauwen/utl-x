---
title: slugify
description: "slugify — UTL-X String function. Convert a string to a URL-safe slug (lowercase, hyphens, no special"
pageClass: stdlib-page
---

# slugify

<p class="stdlib-meta"><code>slugify(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to a URL-safe slug (lowercase, hyphens, no special
chars).

- `string` (required): the string to slugify

``` utlx
slugify("Hello World! 2026")             // "hello-world-2026"
slugify("Cafe Resume")                   // "cafe-resume"
```

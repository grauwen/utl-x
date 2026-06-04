---
title: fromDotCase
description: "fromDotCase — UTL-X String function. Convert from dot.case to separate words."
pageClass: stdlib-page
---

# fromDotCase

<p class="stdlib-meta"><code>fromDotCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from dot.case to separate words.

- `string` (required): dot.case string

``` utlx
fromDotCase("config.max.retries")        // "config max retries"
{
  words: fromDotCase($input.propertyPath)
}
```

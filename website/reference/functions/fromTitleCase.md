---
title: fromTitleCase
description: "fromTitleCase — UTL-X String function. Convert from Title Case to separate lowercase words."
pageClass: stdlib-page
---

# fromTitleCase

<p class="stdlib-meta"><code>fromTitleCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from Title Case to separate lowercase words.

- `string` (required): Title Case string

``` utlx
fromTitleCase("My Title Case")           // "my title case"
{
  normalized: fromTitleCase($input.heading)
}
```

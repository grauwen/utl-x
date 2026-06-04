---
title: fromPathCase
description: "fromPathCase — UTL-X String function. Convert from path/case to separate words."
pageClass: stdlib-page
---

# fromPathCase

<p class="stdlib-meta"><code>fromPathCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from path/case to separate words.

- `string` (required): path/case string

``` utlx
fromPathCase("my/path/name")             // "my path name"
{
  label: fromPathCase($input.route)
}
```

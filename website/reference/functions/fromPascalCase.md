---
title: fromPascalCase
description: "fromPascalCase — UTL-X String function. Convert from PascalCase to separate words."
pageClass: stdlib-page
---

# fromPascalCase

<p class="stdlib-meta"><code>fromPascalCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from PascalCase to separate words.

- `string` (required): PascalCase string

``` utlx
fromPascalCase("MyClassName")            // "my class name"
{
  label: fromPascalCase($input.className)
}
```

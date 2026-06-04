---
title: fromKebabCase
description: "fromKebabCase — UTL-X String function. Convert from kebab-case to separate words."
pageClass: stdlib-page
---

# fromKebabCase

<p class="stdlib-meta"><code>fromKebabCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from kebab-case to separate words.

- `string` (required): kebab-case string

``` bash
echo '{"slug": "my-page-title"}' | utlx -e 'fromKebabCase($input.slug)'
# "my page title"
```

``` utlx
{
  title: titleCase(fromKebabCase($input.slug))
}
```

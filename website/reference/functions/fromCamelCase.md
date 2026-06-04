---
title: fromCamelCase
description: "fromCamelCase — UTL-X String function. Convert from camelCase to separate words."
pageClass: stdlib-page
---

# fromCamelCase

<p class="stdlib-meta"><code>fromCamelCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from camelCase to separate words.

- `string` (required): camelCase string

``` bash
echo '{"name": "myVariableName"}' | utlx -e 'fromCamelCase($input.name)'
# "my variable name"
```

``` utlx
{
  words: fromCamelCase($input.fieldName)
}
```

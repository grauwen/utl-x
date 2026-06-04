---
title: fromSnakeCase
description: "fromSnakeCase — UTL-X String function. Convert from snake_case to separate words."
pageClass: stdlib-page
---

# fromSnakeCase

<p class="stdlib-meta"><code>fromSnakeCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from snake_case to separate words.

- `string` (required): snake_case string

``` bash
echo '{"col": "first_name"}' | utlx -e 'fromSnakeCase($input.col)'
# "first name"
```

``` utlx
{
  label: titleCase(fromSnakeCase($input.columnName))
}
```

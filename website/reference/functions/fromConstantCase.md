---
title: fromConstantCase
description: "fromConstantCase — UTL-X String function. Convert from CONSTANT_CASE to separate words."
pageClass: stdlib-page
---

# fromConstantCase

<p class="stdlib-meta"><code>fromConstantCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from CONSTANT_CASE to separate words.

- `string` (required): CONSTANT_CASE string

``` bash
echo '{"name": "MAX_RETRY_COUNT"}' | utlx -e 'fromConstantCase($input.name)'
# "max retry count"
```

``` utlx
{
  readable: fromConstantCase($input.envVar)
}
```

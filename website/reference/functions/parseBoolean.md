---
title: parseBoolean
description: "parseBoolean — UTL-X Type function. Parse a string or number into a boolean. Accepts 'true', 'false',"
pageClass: stdlib-page
---

# parseBoolean

<p class="stdlib-meta"><code>parseBoolean(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Parse a string or number into a boolean. Accepts `"true"`, `"false"`,
`"yes"`, `"no"`, `"1"`, `"0"`, etc.

- `value` (required): string or number to parse

``` bash
echo '{"active": "yes"}' | utlx -e 'parseBoolean($input.active)'
# true
```

``` utlx
{
  enabled: parseBoolean($input.flags.enabled)
}
```

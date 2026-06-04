---
title: equals
description: "equals — UTL-X Type function. Deep equality comparison of two values. Works for all types including"
pageClass: stdlib-page
---

# equals

<p class="stdlib-meta"><code>equals(a, b) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Deep equality comparison of two values. Works for all types including
objects and arrays.

- `a` (required): first value

- `b` (required): second value

``` bash
echo '{"a": [1,2,3], "b": [1,2,3]}' | utlx -e 'equals($input.a, $input.b)'
# true
```

``` utlx
{
  same: equals($input.expected, $input.actual),
  match: equals($input.config, $input.defaults)
}
```

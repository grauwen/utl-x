---
title: ifThenElse
description: "ifThenElse — UTL-X Type function. Inline if-then-else conditional expression. Returns thenValue if"
pageClass: stdlib-page
---

# ifThenElse

<p class="stdlib-meta"><code>ifThenElse(condition, thenValue, elseValue) → any</code> · <a href="/reference/stdlib#type">Type</a></p>

Inline if-then-else conditional expression. Returns `thenValue` if
condition is true, otherwise `elseValue`.

- `condition` (required): boolean condition

- `thenValue` (required): value if true

- `elseValue` (required): value if false

``` bash
echo '{"age": 20}' | utlx -e 'ifThenElse($input.age >= 18, "adult", "minor")'
# "adult"
```

``` utlx
{
  status: ifThenElse($input.active, "ACTIVE", "INACTIVE"),
  label: ifThenElse($input.count > 0, concat(toString($input.count), " items"), "empty")
}
```

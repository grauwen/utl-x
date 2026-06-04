---
title: not
description: "not — UTL-X Type function. Logical NOT. Negates a boolean value."
pageClass: stdlib-page
---

# not

<p class="stdlib-meta"><code>not(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Logical NOT. Negates a boolean value.

- `value` (required): boolean value

``` utlx
{
  negTrue: not(true),                        // false
  negFalse: not(false),                      // true
  hasName: not(isEmpty($input.name))         // true if name is non-empty
}
```

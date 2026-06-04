---
title: and
description: "and — UTL-X Type function. Logical AND — returns true only if all arguments are truthy."
pageClass: stdlib-page
---

# and

<p class="stdlib-meta"><code>and(values...) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Logical AND — returns true only if all arguments are truthy.

- `values` (variadic): boolean values to combine

``` bash
echo '{"active": true, "verified": true, "paid": false}' \
  | utlx -e 'and($input.active, $input.verified, $input.paid)'
# false (all must be true, but paid is false)
```

``` utlx
// Input: {"active": true, "verified": true, "paid": true}
{
  canShip: and($input.active, $input.verified, $input.paid),  // true
  canRefund: and($input.paid, $input.delivered)               // false if delivered is null
}
```

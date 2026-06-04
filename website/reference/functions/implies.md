---
title: implies
description: "implies — UTL-X Type function. Logical implication (material conditional). Returns false only when"
pageClass: stdlib-page
---

# implies

<p class="stdlib-meta"><code>implies(a, b) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Logical implication (material conditional). Returns `false` only when
`a` is true and `b` is false.

- `a` (required): antecedent boolean

- `b` (required): consequent boolean

``` utlx
implies(true, true)                      // true
implies(true, false)                     // false
implies(false, true)                     // true
implies(false, false)                    // true

{
  valid: implies($input.isPremium, $input.hasSubscription)
}
```

---
title: deepClone
description: "deepClone — UTL-X Object function. Create a deep (recursive) copy of an object. Modifications to the clone"
pageClass: stdlib-page
---

# deepClone

<p class="stdlib-meta"><code>deepClone(object) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Create a deep (recursive) copy of an object. Modifications to the clone
do not affect the original.

- `object` (required): the object to clone

``` utlx
let original = {nested: {value: 42}}
let copy = deepClone(original)
// copy is a fully independent copy — no shared references
{
  cloned: copy    // {nested: {value: 42}}
}
```

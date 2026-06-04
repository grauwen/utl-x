---
title: assertEqual
description: "assertEqual — UTL-X System function. Assert two values are equal. Throws an error showing both values if they"
pageClass: stdlib-page
---

# assertEqual

<p class="stdlib-meta"><code>assertEqual(actual, expected) → null</code> · <a href="/reference/stdlib#system">System</a></p>

Assert two values are equal. Throws an error showing both values if they
differ.

- `actual` (required): the value to test

- `expected` (required): the expected value

``` utlx
assertEqual(count($input.items), 3)
assertEqual($input.status, "ACTIVE")
```

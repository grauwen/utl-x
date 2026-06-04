---
title: assert
description: "assert — UTL-X System function. Assert that a condition is true. Throws an error with the message if"
pageClass: stdlib-page
---

# assert

<p class="stdlib-meta"><code>assert(condition, message?) → null</code> · <a href="/reference/stdlib#system">System</a></p>

Assert that a condition is true. Throws an error with the message if
condition is false.

- `condition` (required): boolean expression to verify

- `message` (optional): error message if assertion fails

``` utlx
assert(count($input.items) > 0, "Input must have at least one item")
assert($input.amount >= 0, "Amount cannot be negative")
```

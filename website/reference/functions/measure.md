---
title: measure
description: "measure — UTL-X System function. Measure execution time of an expression. Returns an object with the"
pageClass: stdlib-page
---

# measure

<p class="stdlib-meta"><code>measure(fn) → object</code> · <a href="/reference/stdlib#system">System</a></p>

Measure execution time of an expression. Returns an object with the
result and elapsed time.

- `fn` (required): lambda `() -> value` to measure

``` utlx
let m = measure(() -> map($input.items, (x) -> x * 2))
// m = {result: [...], elapsed: 12.5, unit: "ms"}

{
  data: m.result,
  timing: m.elapsed
}
```

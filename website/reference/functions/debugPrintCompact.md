---
title: debugPrintCompact
description: "debugPrintCompact — UTL-X System function. Create a compact single-line debug representation of a UDM value."
pageClass: stdlib-page
---

# debugPrintCompact

<p class="stdlib-meta"><code>debugPrintCompact(value, label?) → string</code> · <a href="/reference/stdlib#system">System</a></p>

Create a compact single-line debug representation of a UDM value.

- `value` (required): value to represent

- `label` (optional): label prefix

``` utlx
{
  dump: debugPrintCompact($input, "payload")
}
// Output: {"dump": "[payload] {name:Alice,age:30}"}
```

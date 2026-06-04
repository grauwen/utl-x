---
title: debugPrint
description: "debugPrint — UTL-X System function. Create a human-readable debug representation of a UDM value (multi-line,"
pageClass: stdlib-page
---

# debugPrint

<p class="stdlib-meta"><code>debugPrint(value, label?, indent?) → string</code> · <a href="/reference/stdlib#system">System</a></p>

Create a human-readable debug representation of a UDM value (multi-line,
indented).

- `value` (required): value to represent

- `label` (optional): label prefix

- `indent` (optional): indentation level

``` utlx
{
  dump: debugPrint($input, "request")
}
// Output: {"dump": "[request] {name: \"Alice\", age: 30, ...}"}
```

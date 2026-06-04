---
title: renderCsv
description: "renderCsv — UTL-X CSV function. Render a UDM array as a CSV string. See Chapter 25."
pageClass: stdlib-page
---

# renderCsv

<p class="stdlib-meta"><code>renderCsv(value) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Render a UDM array as a CSV string. See Chapter 25.

- `value` (required): array of objects (each object becomes a row)

``` utlx
renderCsv([{name: "Alice", age: 30}, {name: "Bob", age: 25}])
// "name,age\nAlice,30\nBob,25"
```

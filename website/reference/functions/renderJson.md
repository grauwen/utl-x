---
title: renderJson
description: "renderJson — UTL-X Format function. Serialize a UDM value to a JSON string. See Chapter 24."
pageClass: stdlib-page
---

# renderJson

<p class="stdlib-meta"><code>renderJson(value, pretty?) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Serialize a UDM value to a JSON string. See Chapter 24.

- `value` (required): UDM value to serialize

- `pretty` (optional, default false): pretty-print with indentation

``` utlx
renderJson({name: "Alice", age: 30})        // '{"name":"Alice","age":30}'
renderJson({name: "Alice", age: 30}, true)  // pretty-printed with newlines
```

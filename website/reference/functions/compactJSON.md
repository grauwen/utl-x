---
title: compactJSON
description: "compactJSON — UTL-X JSON function. Compact a JSON string by removing all unnecessary whitespace."
pageClass: stdlib-page
---

# compactJSON

<p class="stdlib-meta"><code>compactJSON(json, options?, indent?) → string</code> · <a href="/reference/stdlib#json">JSON</a></p>

Compact a JSON string by removing all unnecessary whitespace.

- `json` (required): JSON string to compact

- `options` (optional): formatting options

- `indent` (optional): indentation level

``` bash
echo '{"data": {"name": "Alice", "age": 30}}' | utlx -e 'compactJSON(renderJson($input))'
# {"name":"Alice","age":30}
```

``` utlx
// See Chapter 24 for JSON processing.
{
  minified: compactJSON(renderJson($input))
}
```

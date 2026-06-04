---
title: prettyPrint
description: "prettyPrint — UTL-X Format function. Auto-detect format and pretty-print a string (JSON, XML, or YAML)."
pageClass: stdlib-page
---

# prettyPrint

<p class="stdlib-meta"><code>prettyPrint(string, indent?) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Auto-detect format and pretty-print a string (JSON, XML, or YAML).

- `string` (required): the string to format

- `indent` (optional): indentation size

``` utlx
prettyPrint($input.compactJson)          // auto-detects JSON and formats
```

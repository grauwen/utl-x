---
title: prettyPrintJSON
description: "prettyPrintJSON — UTL-X Format function. Pretty-print a JSON string with optional indentation."
pageClass: stdlib-page
---

# prettyPrintJSON

<p class="stdlib-meta"><code>prettyPrintJSON(json, indent?) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Pretty-print a JSON string with optional indentation.

- `json` (required): JSON string to format

- `indent` (optional): indentation size (default 2)

``` utlx
prettyPrintJSON($input.compactJson)      // formatted with 2-space indent
```

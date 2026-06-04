---
title: prettyPrintFormat
description: "prettyPrintFormat — UTL-X Format function. Pretty-print a UDM object in the specified format."
pageClass: stdlib-page
---

# prettyPrintFormat

<p class="stdlib-meta"><code>prettyPrintFormat(value, format) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Pretty-print a UDM object in the specified format.

- `value` (required): UDM value to format

- `format` (required): `"json"`, `"xml"`, or `"yaml"`

``` utlx
prettyPrintFormat($input, "json")        // indented JSON output
```

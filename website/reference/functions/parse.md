---
title: parse
description: "parse — UTL-X Format function. Parse a string into a navigable UDM value, auto-detecting format or"
pageClass: stdlib-page
---

# parse

<p class="stdlib-meta"><code>parse(string, format?) → value</code> · <a href="/reference/stdlib#format">Format</a></p>

Parse a string into a navigable UDM value, auto-detecting format or
using the specified format.

- `string` (required): the string to parse

- `format` (optional, default auto-detect): `"json"`, `"xml"`, `"yaml"`,
  `"csv"`

``` utlx
// Parse XML from a CDATA section:
let innerXml = parse($input.Payload, "xml")
innerXml.Order.Customer                  // "Acme Corp"

// Auto-detect format:
let parsed = parse($input.rawData)       // auto-detects JSON, XML, or YAML
```

For normal file processing, use `input json`/`input xml` in the header —
these functions are for the embedded-format-as-value case.

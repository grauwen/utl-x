---
title: parseCsv
description: "parseCsv — UTL-X Format function. Parse a CSV string into an array of rows (objects if headers, arrays if"
pageClass: stdlib-page
---

# parseCsv

<p class="stdlib-meta"><code>parseCsv(string, options?) → array</code> · <a href="/reference/stdlib#format">Format</a></p>

Parse a CSV string into an array of rows (objects if headers, arrays if
not). See Chapter 25.

- `string` (required): the CSV string to parse

- `options` (optional): `{headers: false}`, `{delimiter: ";"}`

``` utlx
let data = parseCsv($input.csvData, {delimiter: ";", headers: true})
data[0].Name                             // first row, Name column
```

Also: `render(value, format, pretty?)`, `renderJson(value, pretty?)`,
`renderXml(value)`, `renderYaml(value)`, `renderCsv(value)`.

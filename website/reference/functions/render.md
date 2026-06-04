---
title: render
description: "render — UTL-X Format function. Serialize a UDM value to a string in the specified format."
pageClass: stdlib-page
---

# render

<p class="stdlib-meta"><code>render(value, format, pretty?) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Serialize a UDM value to a string in the specified format.

- `value` (required): UDM value to serialize

- `format` (required): `"json"`, `"xml"`, `"yaml"`, `"csv"`

- `pretty` (optional, default false): pretty-print with indentation

``` utlx
render({name: "Alice"}, "json", true)    // pretty-printed JSON
render({Order: {Id: "1"}}, "xml")        // "<Order><Id>1</Id></Order>"

// Use case: embed XML inside a JSON field (CDATA pattern)
{
  messageId: generateUuid(),
  payload: render($input, "xml")         // XML as a string value
}
```

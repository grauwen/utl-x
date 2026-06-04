---
title: prettyPrintXML
description: "prettyPrintXML — UTL-X Format function. Pretty-print an XML string with optional formatting options."
pageClass: stdlib-page
---

# prettyPrintXML

<p class="stdlib-meta"><code>prettyPrintXML(xml, options?) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Pretty-print an XML string with optional formatting options.

- `xml` (required): XML string to format

- `options` (optional): formatting options

``` utlx
prettyPrintXML($input.xmlPayload)        // indented XML
```

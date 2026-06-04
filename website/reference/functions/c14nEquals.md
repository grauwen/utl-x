---
title: c14nEquals
description: "c14nEquals — UTL-X XML function. Compare two XML documents semantically (ignoring formatting differences)"
pageClass: stdlib-page
---

# c14nEquals

<p class="stdlib-meta"><code>c14nEquals(xml1, xml2) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Compare two XML documents semantically (ignoring formatting differences)
by comparing their canonical forms. See Chapter 22.

- `xml1` (required): first XML UDM value

- `xml2` (required): second XML UDM value

``` utlx
{
  match: c14nEquals($input.expected, $input.actual),
  status: if (c14nEquals($input.expected, $input.actual)) "PASS" else "FAIL"
}
```

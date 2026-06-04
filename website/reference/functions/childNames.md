---
title: childNames
description: "childNames — UTL-X XML function. Get the names of all child elements."
pageClass: stdlib-page
---

# childNames

<p class="stdlib-meta"><code>childNames(element) → array</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get the names of all child elements.

- `element` (required): XML UDM element

``` utlx
// Input: <Order><Id>1</Id><Date>2026-05-01</Date><Total>100</Total></Order>
// See Chapter 22 for XML processing.
{
  fields: childNames($input)    // ["Id", "Date", "Total"]
}
```

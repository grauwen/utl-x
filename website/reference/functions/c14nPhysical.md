---
title: c14nPhysical
description: "c14nPhysical — UTL-X XML function. Canonicalize XML using Physical Canonical XML (preserves physical"
pageClass: stdlib-page
---

# c14nPhysical

<p class="stdlib-meta"><code>c14nPhysical(xml, options?) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Canonicalize XML using Physical Canonical XML (preserves physical
structure more faithfully).

- `xml` (required): XML UDM value

- `options` (optional): canonicalization options

``` utlx
{
  physical: c14nPhysical($input)
}
```

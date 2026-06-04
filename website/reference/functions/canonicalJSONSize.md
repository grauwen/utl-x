---
title: canonicalJSONSize
description: "canonicalJSONSize — UTL-X JSON function. Get the size in bytes (UTF-8) of the canonical JSON form."
pageClass: stdlib-page
---

# canonicalJSONSize

<p class="stdlib-meta"><code>canonicalJSONSize(json) → number</code> · <a href="/reference/stdlib#json">JSON</a></p>

Get the size in bytes (UTF-8) of the canonical JSON form.

- `json` (required): JSON string or UDM value

``` utlx
// See Chapter 24 for JSON processing.
{
  sizeBytes: canonicalJSONSize(renderJson($input))
}
```

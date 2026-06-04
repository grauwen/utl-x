---
title: canonicalJSONHash
description: "canonicalJSONHash — UTL-X JSON function. Canonicalize JSON per RFC 8785 and compute a cryptographic hash of the"
pageClass: stdlib-page
---

# canonicalJSONHash

<p class="stdlib-meta"><code>canonicalJSONHash(json, algorithm?) → string</code> · <a href="/reference/stdlib#json">JSON</a></p>

Canonicalize JSON per RFC 8785 and compute a cryptographic hash of the
result.

- `json` (required): JSON string or UDM value

- `algorithm` (optional, default `"SHA-256"`): hash algorithm

``` utlx
// See Chapter 24 for JSON processing.
{
  digest: canonicalJSONHash(renderJson($input)),
  digest512: canonicalJSONHash(renderJson($input), "SHA-512")
}
```

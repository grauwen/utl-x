---
title: jcs
description: "jcs — UTL-X JSON function. JSON Canonicalization Scheme (RFC 8785). Produces deterministic JSON —"
pageClass: stdlib-page
---

# jcs

<p class="stdlib-meta"><code>jcs(json) → string</code> · <a href="/reference/stdlib#json">JSON</a></p>

JSON Canonicalization Scheme (RFC 8785). Produces deterministic JSON —
identical output regardless of key order or whitespace. See Chapter 24.

- `json` (required): JSON UDM value to canonicalize

``` bash
echo '{"z": 3, "a": 1, "m": 2}' | utlx -e 'jcs($input)'
# {"a":1,"m":2,"z":3}  (keys sorted, no whitespace)
```

Also: `canonicalJSONHash(json, algorithm?)` (hash the canonical form),
`canonicalJSONSize(json)` (byte size), `isCanonicalJSON(string)`.

---
title: canonicalizeJSON
description: "canonicalizeJSON — UTL-X JSON function. Alias for jcs(). Produces deterministic JSON using RFC 8785."
pageClass: stdlib-page
---

# canonicalizeJSON

<p class="stdlib-meta"><code>canonicalizeJSON(json) → string</code> · <a href="/reference/stdlib#json">JSON</a></p>

Alias for `jcs()`. Produces deterministic JSON using RFC 8785.

- `json` (required): JSON UDM value to canonicalize

``` bash
echo '{"z": 3, "a": 1, "m": 2}' | utlx -e 'canonicalizeJSON($input)'
# {"a":1,"m":2,"z":3}  (keys sorted, no whitespace)
```

---
title: isCanonicalJSON
description: "isCanonicalJSON — UTL-X JSON function. Validates that a JSON string is in canonical form per RFC 8785 (JSON"
pageClass: stdlib-page
---

# isCanonicalJSON

<p class="stdlib-meta"><code>isCanonicalJSON(json) → boolean</code> · <a href="/reference/stdlib#json">JSON</a></p>

Validates that a JSON string is in canonical form per RFC 8785 (JSON
Canonicalization Scheme). See Chapter 24.

- `json` (required): JSON string to validate

``` bash
echo '{"json": "{\"a\":1,\"b\":2}"}' | utlx -e 'isCanonicalJSON($input.json)'
# true
```

``` utlx
{
  isCanonical: isCanonicalJSON(renderJson($input))
}
```

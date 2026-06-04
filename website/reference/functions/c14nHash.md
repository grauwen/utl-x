---
title: c14nHash
description: "c14nHash — UTL-X XML function. Compute a hash digest of the canonical form of an XML document."
pageClass: stdlib-page
---

# c14nHash

<p class="stdlib-meta"><code>c14nHash(xml, algorithm?) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Compute a hash digest of the canonical form of an XML document.

- `xml` (required): XML UDM value to canonicalize and hash

- `algorithm` (optional, default `"SHA-256"`): hash algorithm (e.g.,
  `"SHA-512"`)

``` bash
echo '<Invoice id="1"><Total>100</Total></Invoice>' \
  | utlx -f xml -e 'c14nHash($input)'
# "a1b2c3d4e5..."  (SHA-256 hex digest of canonical form)
```

``` utlx
{
  digest256: c14nHash($input),
  digest512: c14nHash($input, "SHA-512")
}
```

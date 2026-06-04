---
title: getJWSSigningInput
description: "getJWSSigningInput — UTL-X Security function. Extracts the signing input (header.payload) from a JWS token for manual"
pageClass: stdlib-page
---

# getJWSSigningInput

<p class="stdlib-meta"><code>getJWSSigningInput(token) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Extracts the signing input (header.payload) from a JWS token for manual
verification. See Chapter 38.

- `token` (required): JWS/JWT token string

``` utlx
{
  signingInput: getJWSSigningInput($input.token)
}
```

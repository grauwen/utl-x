---
title: decodeJWS
description: "decodeJWS — UTL-X Security function. Decode a JWS (JSON Web Signature) token WITHOUT verifying the signature."
pageClass: stdlib-page
---

# decodeJWS

<p class="stdlib-meta"><code>decodeJWS(token) → object</code> · <a href="/reference/stdlib#security">Security</a></p>

Decode a JWS (JSON Web Signature) token WITHOUT verifying the signature.
Returns header and payload.

- `token` (required): JWS token string

``` utlx
{
  decoded: decodeJWS($input.token),
  algorithm: decodeJWS($input.token).header.alg
}
```

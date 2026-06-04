---
title: getJWSAlgorithm
description: "getJWSAlgorithm — UTL-X Security function. Gets the algorithm (alg) from a JWS token header. See Chapter 38."
pageClass: stdlib-page
---

# getJWSAlgorithm

<p class="stdlib-meta"><code>getJWSAlgorithm(token) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Gets the algorithm (alg) from a JWS token header. See Chapter 38.

- `token` (required): JWS/JWT token string

``` utlx
{
  algorithm: getJWSAlgorithm($input.token)   // "RS256"
}
```

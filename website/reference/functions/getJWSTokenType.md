---
title: getJWSTokenType
description: "getJWSTokenType — UTL-X Security function. Gets the token type (typ) from a JWS token header. See Chapter 38."
pageClass: stdlib-page
---

# getJWSTokenType

<p class="stdlib-meta"><code>getJWSTokenType(token) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Gets the token type (typ) from a JWS token header. See Chapter 38.

- `token` (required): JWS/JWT token string

``` utlx
{
  type: getJWSTokenType($input.token)        // "JWT"
}
```

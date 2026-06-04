---
title: getJWTClaims
description: "getJWTClaims — UTL-X Security function. Extracts all claims from a JWT payload WITHOUT verification. See Chapter"
pageClass: stdlib-page
---

# getJWTClaims

<p class="stdlib-meta"><code>getJWTClaims(token) → object</code> · <a href="/reference/stdlib#security">Security</a></p>

Extracts all claims from a JWT payload WITHOUT verification. See Chapter
38.

- `token` (required): JWT token string

``` utlx
let claims = getJWTClaims($input.token)
{
  subject: claims.sub,
  issuer: claims.iss,
  expiry: claims.exp
}
```

---
title: getJWTIssuer
description: "getJWTIssuer — UTL-X Security function. Gets the issuer (iss) claim from a JWT token. See Chapter 38."
pageClass: stdlib-page
---

# getJWTIssuer

<p class="stdlib-meta"><code>getJWTIssuer(token) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Gets the issuer (iss) claim from a JWT token. See Chapter 38.

- `token` (required): JWT token string

``` utlx
{
  issuer: getJWTIssuer($input.token)
}
```

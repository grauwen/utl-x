---
title: getJWTClaim
description: "getJWTClaim — UTL-X Security function. Gets a specific claim from a JWT token payload. See Chapter 38."
pageClass: stdlib-page
---

# getJWTClaim

<p class="stdlib-meta"><code>getJWTClaim(token, claim) → any</code> · <a href="/reference/stdlib#security">Security</a></p>

Gets a specific claim from a JWT token payload. See Chapter 38.

- `token` (required): JWT token string

- `claim` (required): claim name (e.g., "sub", "email", custom claims)

``` utlx
{
  email: getJWTClaim($input.token, "email"),
  role: getJWTClaim($input.token, "role")
}
```

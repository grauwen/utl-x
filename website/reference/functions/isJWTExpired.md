---
title: isJWTExpired
description: "isJWTExpired — UTL-X Security function. Checks if a JWT is expired based on its exp claim. See Chapter 38."
pageClass: stdlib-page
---

# isJWTExpired

<p class="stdlib-meta"><code>isJWTExpired(token) → boolean</code> · <a href="/reference/stdlib#security">Security</a></p>

Checks if a JWT is expired based on its `exp` claim. See Chapter 38.

- `token` (required): JWT token string

``` utlx
{
  expired: isJWTExpired($input.token),
  needsRefresh: isJWTExpired($input.accessToken)
}
```

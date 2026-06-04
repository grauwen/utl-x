---
title: getJWTAudience
description: "getJWTAudience — UTL-X Security function. Gets the audience (aud) claim from a JWT token. See Chapter 38."
pageClass: stdlib-page
---

# getJWTAudience

<p class="stdlib-meta"><code>getJWTAudience(token) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Gets the audience (aud) claim from a JWT token. See Chapter 38.

- `token` (required): JWT token string

``` utlx
{
  audience: getJWTAudience($input.token)
}
```

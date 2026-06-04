---
title: getJWSKeyId
description: "getJWSKeyId — UTL-X Security function. Gets the Key ID (kid) from a JWS token header. See Chapter 38."
pageClass: stdlib-page
---

# getJWSKeyId

<p class="stdlib-meta"><code>getJWSKeyId(token) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Gets the Key ID (kid) from a JWS token header. See Chapter 38.

- `token` (required): JWS/JWT token string

``` utlx
{
  keyId: getJWSKeyId($input.token)
}
```

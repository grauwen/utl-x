---
title: getJWSPayload
description: "getJWSPayload — UTL-X Security function. Extracts the payload from a JWS token WITHOUT signature verification."
pageClass: stdlib-page
---

# getJWSPayload

<p class="stdlib-meta"><code>getJWSPayload(token) → object</code> · <a href="/reference/stdlib#security">Security</a></p>

Extracts the payload from a JWS token WITHOUT signature verification.
See Chapter 38.

- `token` (required): JWS/JWT token string

``` utlx
{
  payload: getJWSPayload($input.token)
}
```

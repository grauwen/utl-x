---
title: getJWSHeader
description: "getJWSHeader — UTL-X Security function. Extracts the complete header from a JWS token as an object. See Chapter"
pageClass: stdlib-page
---

# getJWSHeader

<p class="stdlib-meta"><code>getJWSHeader(token) → object</code> · <a href="/reference/stdlib#security">Security</a></p>

Extracts the complete header from a JWS token as an object. See Chapter
38.

- `token` (required): JWS/JWT token string

``` utlx
{
  header: getJWSHeader($input.token)
  // {"alg": "RS256", "typ": "JWT", "kid": "key-1"}
}
```

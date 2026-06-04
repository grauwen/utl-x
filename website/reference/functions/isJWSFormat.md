---
title: isJWSFormat
description: "isJWSFormat — UTL-X Security function. Checks if a string is in valid JWS format (three Base64URL segments"
pageClass: stdlib-page
---

# isJWSFormat

<p class="stdlib-meta"><code>isJWSFormat(token) → boolean</code> · <a href="/reference/stdlib#security">Security</a></p>

Checks if a string is in valid JWS format (three Base64URL segments
separated by dots). See Chapter 38.

- `token` (required): string to validate

``` utlx
{
  validFormat: isJWSFormat($input.token),
  canDecode: isJWSFormat($input.authHeader)
}
```

---
title: getJWSInfo
description: "getJWSInfo — UTL-X Security function. Gets information about the JWS token structure (header, payload size,"
pageClass: stdlib-page
---

# getJWSInfo

<p class="stdlib-meta"><code>getJWSInfo(token) → object</code> · <a href="/reference/stdlib#security">Security</a></p>

Gets information about the JWS token structure (header, payload size,
etc.). See Chapter 38.

- `token` (required): JWS/JWT token string

``` utlx
{
  info: getJWSInfo($input.token)
}
```

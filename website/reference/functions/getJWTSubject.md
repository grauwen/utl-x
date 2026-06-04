---
title: getJWTSubject
description: "getJWTSubject — UTL-X Security function. Gets the subject (sub) claim from a JWT token. See Chapter 38."
pageClass: stdlib-page
---

# getJWTSubject

<p class="stdlib-meta"><code>getJWTSubject(token) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Gets the subject (sub) claim from a JWT token. See Chapter 38.

- `token` (required): JWT token string

``` utlx
{
  subject: getJWTSubject($input.token)
}
```

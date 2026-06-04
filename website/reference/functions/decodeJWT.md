---
title: decodeJWT
description: "decodeJWT — UTL-X Security function. Decode a JWT token WITHOUT verification. Returns header, payload"
pageClass: stdlib-page
---

# decodeJWT

<p class="stdlib-meta"><code>decodeJWT(token) → object</code> · <a href="/reference/stdlib#security">Security</a></p>

Decode a JWT token WITHOUT verification. Returns header, payload
(claims), and signature.

- `token` (required): JWT token string

``` bash
echo '{"token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.sig"}' \
  | utlx -e 'decodeJWT($input.token).payload.sub'
# "user1"
```

``` utlx
let jwt = decodeJWT($input.authToken)
{
  subject: jwt.payload.sub,
  issuer: jwt.payload.iss,
  expired: jwt.payload.exp < timestamp()
}
```

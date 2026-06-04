---
title: sha1
description: "sha1 — UTL-X Security function. Compute a SHA-1 hash. Returns 40-char hex string. Avoid for security"
pageClass: stdlib-page
---

# sha1

<p class="stdlib-meta"><code>sha1(data) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Compute a SHA-1 hash. Returns 40-char hex string. Avoid for security
purposes. See Chapter 38.

- `data` (required): string to hash

``` utlx
sha1("sensitive data")                   // 40-char hex string (avoid for security)
```

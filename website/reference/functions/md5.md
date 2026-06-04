---
title: md5
description: "md5 — UTL-X Security function. Compute an MD5 hash. Returns hex-encoded digest string. See Chapter 38."
pageClass: stdlib-page
---

# md5

<p class="stdlib-meta"><code>md5(data) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Compute an MD5 hash. Returns hex-encoded digest string. See Chapter 38.

- `data` (required): string to hash

``` utlx
md5("hello")                             // "5d41402abc4b2a76b9719d911017c592"
{
  checksum: md5($input.content)
}
```

**Anti-pattern:** `md5()` for security — MD5 is cryptographically
broken. Use `sha256()` minimum. MD5 is acceptable only for non-security
checksums (file deduplication, cache keys).

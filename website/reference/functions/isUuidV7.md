---
title: isUuidV7
description: "isUuidV7 — UTL-X Security function. Check if a UUID string is specifically version 7 (time-ordered). See"
pageClass: stdlib-page
---

# isUuidV7

<p class="stdlib-meta"><code>isUuidV7(uuid) → boolean</code> · <a href="/reference/stdlib#security">Security</a></p>

Check if a UUID string is specifically version 7 (time-ordered). See
Chapter 38.

- `uuid` (required): UUID string to check

``` utlx
{
  isV7: isUuidV7($input.messageId),
  canExtractTime: isUuidV7($input.id)
}
```

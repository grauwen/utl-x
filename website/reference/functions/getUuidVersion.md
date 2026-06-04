---
title: getUuidVersion
description: "getUuidVersion — UTL-X Security function. Get the version number from a UUID string. See Chapter 38."
pageClass: stdlib-page
---

# getUuidVersion

<p class="stdlib-meta"><code>getUuidVersion(uuid) → number</code> · <a href="/reference/stdlib#security">Security</a></p>

Get the version number from a UUID string. See Chapter 38.

- `uuid` (required): UUID string

``` bash
echo '{"id": "550e8400-e29b-41d4-a716-446655440000"}' | utlx -e 'getUuidVersion($input.id)'
# 4
```

``` utlx
{
  version: getUuidVersion($input.correlationId)
}
```

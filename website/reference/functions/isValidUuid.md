---
title: isValidUuid
description: "isValidUuid — UTL-X Security function. Validate if a string is a properly formatted UUID (any version). See"
pageClass: stdlib-page
---

# isValidUuid

<p class="stdlib-meta"><code>isValidUuid(uuid) → boolean</code> · <a href="/reference/stdlib#security">Security</a></p>

Validate if a string is a properly formatted UUID (any version). See
Chapter 38.

- `uuid` (required): string to validate

``` bash
echo '{"id": "550e8400-e29b-41d4-a716-446655440000"}' | utlx -e 'isValidUuid($input.id)'
# true
```

``` utlx
{
  valid: isValidUuid($input.correlationId),
  error: if (!isValidUuid($input.id)) "Invalid UUID format" else null
}
```

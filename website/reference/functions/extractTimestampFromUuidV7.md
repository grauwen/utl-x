---
title: extractTimestampFromUuidV7
description: "extractTimestampFromUuidV7 — UTL-X Security function. Extract the embedded timestamp from a UUID v7 value. See Chapter 38."
pageClass: stdlib-page
---

# extractTimestampFromUuidV7

<p class="stdlib-meta"><code>extractTimestampFromUuidV7(uuid) → datetime</code> · <a href="/reference/stdlib#security">Security</a></p>

Extract the embedded timestamp from a UUID v7 value. See Chapter 38.

- `uuid` (required): UUID v7 string

``` bash
echo '{"id": "018f6c30-a2b0-7000-8000-000000000001"}' | utlx -e 'extractTimestampFromUuidV7($input.id)'
# "2024-05-21T..."
```

``` utlx
{
  createdAt: extractTimestampFromUuidV7($input.messageId),
  age: diffHours(now(), extractTimestampFromUuidV7($input.messageId))
}
```

## F

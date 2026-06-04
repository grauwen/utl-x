---
title: generateUuidV7Batch
description: "generateUuidV7Batch — UTL-X Security function. Generate a batch of UUID v7s with monotonic guarantee (each is greater"
pageClass: stdlib-page
---

# generateUuidV7Batch

<p class="stdlib-meta"><code>generateUuidV7Batch(count) → array</code> · <a href="/reference/stdlib#security">Security</a></p>

Generate a batch of UUID v7s with monotonic guarantee (each is greater
than the previous). See Chapter 38.

- `count` (required): number of UUIDs to generate

``` bash
echo '{"n": 3}' | utlx -e 'generateUuidV7Batch($input.n)'
# ["018f6c30-a2b0-7000-...", "018f6c30-a2b0-7001-...", "018f6c30-a2b0-7002-..."]
```

``` utlx
{
  batchIds: generateUuidV7Batch(10)
}
```

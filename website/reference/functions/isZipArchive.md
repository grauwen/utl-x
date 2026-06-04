---
title: isZipArchive
description: "isZipArchive — UTL-X Binary function. Check if binary data is a zip archive (by checking magic bytes)."
pageClass: stdlib-page
---

# isZipArchive

<p class="stdlib-meta"><code>isZipArchive(data) → boolean</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Check if binary data is a zip archive (by checking magic bytes).

- `data` (required): binary data to check

``` utlx
{
  isZip: isZipArchive($input.fileData),
  entries: if (isZipArchive($input.data)) listZipEntries($input.data) else []
}
```

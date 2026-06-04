---
title: readZipEntry
description: "readZipEntry — UTL-X Binary function. Read a single entry from a zip archive by name."
pageClass: stdlib-page
---

# readZipEntry

<p class="stdlib-meta"><code>readZipEntry(zip, entryName) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read a single entry from a zip archive by name.

- `zip` (required): zip binary data

- `entryName` (required): path inside the archive

``` utlx
let content = readZipEntry($input.archive, "data.json")
{
  parsed: parseJson(binaryToString(content, "UTF-8"))
}
```

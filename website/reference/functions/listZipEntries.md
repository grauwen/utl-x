---
title: listZipEntries
description: "listZipEntries — UTL-X Binary function. List all entries (file paths) in a ZIP archive."
pageClass: stdlib-page
---

# listZipEntries

<p class="stdlib-meta"><code>listZipEntries(zipData) → array</code> · <a href="/reference/stdlib#binary">Binary</a></p>

List all entries (file paths) in a ZIP archive.

- `zipData` (required): binary ZIP data

``` utlx
{
  files: listZipEntries($input.archive)
  // ["readme.txt", "data/orders.csv", "data/customers.csv"]
}
```

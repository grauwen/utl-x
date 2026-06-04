---
title: unzipArchive
description: "unzipArchive — UTL-X Binary function. Extract all files from a zip archive into an object keyed by entry name."
pageClass: stdlib-page
---

# unzipArchive

<p class="stdlib-meta"><code>unzipArchive(zip, options?) → object</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Extract all files from a zip archive into an object keyed by entry name.

- `zip` (required): zip binary data

- `options` (optional): extraction options

``` utlx
let files = unzipArchive($input.archive)
{
  readme: binaryToString(files."README.md", "UTF-8")
}
```

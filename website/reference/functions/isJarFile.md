---
title: isJarFile
description: "isJarFile — UTL-X Binary function. Check if binary data is a JAR file."
pageClass: stdlib-page
---

# isJarFile

<p class="stdlib-meta"><code>isJarFile(data) → boolean</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Check if binary data is a JAR file.

- `data` (required): binary data to check

``` utlx
{
  isJar: isJarFile($input.fileData)
}
```

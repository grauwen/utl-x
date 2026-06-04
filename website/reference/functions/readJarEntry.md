---
title: readJarEntry
description: "readJarEntry — UTL-X Binary function. Read a single entry from a JAR file by name."
pageClass: stdlib-page
---

# readJarEntry

<p class="stdlib-meta"><code>readJarEntry(jar, entryName) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read a single entry from a JAR file by name.

- `jar` (required): JAR binary data

- `entryName` (required): path inside the JAR (e.g.
  `"META-INF/MANIFEST.MF"`)

``` utlx
let manifest = readJarEntry($input.jarData, "META-INF/MANIFEST.MF")
{
  content: binaryToString(manifest, "UTF-8")
}
```

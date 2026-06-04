---
title: listJarEntries
description: "listJarEntries — UTL-X Binary function. List all entries (file paths) in a JAR file."
pageClass: stdlib-page
---

# listJarEntries

<p class="stdlib-meta"><code>listJarEntries(jarData) → array</code> · <a href="/reference/stdlib#binary">Binary</a></p>

List all entries (file paths) in a JAR file.

- `jarData` (required): binary JAR data

``` utlx
{
  entries: listJarEntries($input.jar)
  // ["META-INF/MANIFEST.MF", "com/example/Main.class", ...]
}
```

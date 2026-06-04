---
title: readJarManifest
description: "readJarManifest — UTL-X Binary function. Read and parse the JAR manifest into an object of key-value pairs."
pageClass: stdlib-page
---

# readJarManifest

<p class="stdlib-meta"><code>readJarManifest(jar) → object</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read and parse the JAR manifest into an object of key-value pairs.

- `jar` (required): JAR binary data

``` utlx
let mf = readJarManifest($input.jarData)
{
  version: mf."Implementation-Version"
}
```

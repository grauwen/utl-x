---
title: tempDir
description: "tempDir — UTL-X System function. Get the system temporary directory path."
pageClass: stdlib-page
---

# tempDir

<p class="stdlib-meta"><code>tempDir() → string</code> · <a href="/reference/stdlib#system">System</a></p>

Get the system temporary directory path.

``` utlx
{
  tmpDir: tempDir()
}
```

**Security note:** can be restricted in UTLXe via security policy
(Chapter 38) to prevent revealing host file system paths.

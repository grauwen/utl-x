---
title: homeDir
description: "homeDir — UTL-X System function. Get the current user's home directory path."
pageClass: stdlib-page
---

# homeDir

<p class="stdlib-meta"><code>homeDir() → string</code> · <a href="/reference/stdlib#system">System</a></p>

Get the current user's home directory path.

``` utlx
homeDir()                                // "/Users/alice" or "/home/alice"
{
  home: homeDir()
}
```

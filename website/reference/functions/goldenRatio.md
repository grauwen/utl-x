---
title: goldenRatio
description: "goldenRatio — UTL-X Math function. Returns the golden ratio (approximately 1.61803398874989484820)."
pageClass: stdlib-page
---

# goldenRatio

<p class="stdlib-meta"><code>goldenRatio() → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Returns the golden ratio (approximately 1.61803398874989484820).

``` utlx
goldenRatio()                            // 1.618033988749895
{
  ratio: goldenRatio(),
  scaled: $input.width * goldenRatio()
}
```

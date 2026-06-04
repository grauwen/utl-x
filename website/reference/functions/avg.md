---
title: avg
description: "avg — UTL-X Math function. Average of numeric values in an array. Returns 0 for empty arrays."
pageClass: stdlib-page
---

# avg

<p class="stdlib-meta"><code>avg(array) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Average of numeric values in an array. Returns 0 for empty arrays.

- `array` (required): array of numbers

``` bash
echo '{"scores": [85, 92, 78, 95, 88]}' | utlx -e 'avg($input.scores)'
# 87.6
```

``` utlx
{
  averageScore: avg($input.scores),
  averageEmpty: avg([])                  // 0 (safe on empty arrays)
}
```

**Anti-pattern:** `sum(arr) / count(arr)` — crashes on empty arrays
(division by zero). Use `avg()` which handles this safely.

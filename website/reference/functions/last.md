---
title: last
description: "last — UTL-X Array function. Returns the last element of an array, or null if the array is empty."
pageClass: stdlib-page
---

# last

<p class="stdlib-meta"><code>last(array) → element or null</code> · <a href="/reference/stdlib#array">Array</a></p>

Returns the last element of an array, or `null` if the array is empty.

- `array` (required): the source array

``` utlx
last(["Apple", "Banana", "Cherry"])      // "Cherry"
last([42])                               // 42
last([])                                 // null

// Use case: get the most recent event
{
  latest: last(sortBy($input.events, (e) -> e.timestamp))
}
```

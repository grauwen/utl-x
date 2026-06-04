---
title: truncate
description: "truncate — UTL-X String function. Truncate a string to a maximum length, appending a suffix (default"
pageClass: stdlib-page
---

# truncate

<p class="stdlib-meta"><code>truncate(string, maxLength, suffix?) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Truncate a string to a maximum length, appending a suffix (default
`"..."`).

- `string` (required): the string to truncate

- `maxLength` (required): maximum total length (including suffix)

- `suffix` (optional): suffix to append (default `"..."`)

``` utlx
truncate("Hello World, this is a long string", 15)
// "Hello World,..."

truncate("Hello World", 20)              // "Hello World" (no truncation needed)
```

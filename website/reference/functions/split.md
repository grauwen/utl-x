---
title: split
description: "split — UTL-X String function. Split a string into an array of substrings."
pageClass: stdlib-page
---

# split

<p class="stdlib-meta"><code>split(string, separator) → array</code> · <a href="/reference/stdlib#string">String</a></p>

Split a string into an array of substrings.

- `string` (required): the string to split

- `separator` (required): delimiter string

``` utlx
split("a,b,c", ",")                     // ["a", "b", "c"]
split("Hello World", " ")               // ["Hello", "World"]
split("user@example.com", "@")          // ["user", "example.com"]

// Use case: extract domain from email
let parts = split($input.email, "@")
parts[1]                                 // "example.com"

// Use case: parse a path
split("/usr/local/bin", "/")             // ["", "usr", "local", "bin"]
```

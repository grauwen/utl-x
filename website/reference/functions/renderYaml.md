---
title: renderYaml
description: "renderYaml — UTL-X YAML function. Render a UDM object as a YAML string. See Chapter 26."
pageClass: stdlib-page
---

# renderYaml

<p class="stdlib-meta"><code>renderYaml(value) → string</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Render a UDM object as a YAML string. See Chapter 26.

- `value` (required): UDM value to serialize

``` utlx
renderYaml({database: {host: "localhost", port: 5432}})
// "database:\n  host: localhost\n  port: 5432"
```

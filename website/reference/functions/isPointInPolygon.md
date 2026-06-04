---
title: isPointInPolygon
description: "isPointInPolygon — UTL-X Geospatial function. Checks if a point is inside a polygon using the ray casting algorithm."
pageClass: stdlib-page
---

# isPointInPolygon

<p class="stdlib-meta"><code>isPointInPolygon(lat, lon, polygon) → boolean</code> · <a href="/reference/stdlib#geospatial">Geospatial</a></p>

Checks if a point is inside a polygon using the ray casting algorithm.

- `lat` (required): point latitude

- `lon` (required): point longitude

- `polygon` (required): array of \[lat, lon\] coordinate pairs

``` utlx
let zone = [[52.0, 4.0], [52.5, 4.0], [52.5, 5.0], [52.0, 5.0]]
{
  inZone: isPointInPolygon($input.lat, $input.lon, zone)
}
```

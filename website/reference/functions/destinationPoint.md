---
title: destinationPoint
description: "destinationPoint — UTL-X Geospatial function. Calculate a destination point given a starting point, bearing, and"
pageClass: stdlib-page
---

# destinationPoint

<p class="stdlib-meta"><code>destinationPoint(lat, lon, bearing, distance) → object</code> · <a href="/reference/stdlib#geospatial">Geospatial</a></p>

Calculate a destination point given a starting point, bearing, and
distance.

- `lat` (required): starting latitude

- `lon` (required): starting longitude

- `bearing` (required): bearing in degrees (0-360)

- `distance` (required): distance in kilometers

``` utlx
{
  destination: destinationPoint(52.37, 4.90, 180, 100)
}
// Output: {"lat": 51.47, "lon": 4.90} (approximately 100km due south)
```

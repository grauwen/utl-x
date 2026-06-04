---
title: isPointInCircle
description: "isPointInCircle — UTL-X Geospatial function. Checks if a point is within a circular radius from a center point."
pageClass: stdlib-page
---

# isPointInCircle

<p class="stdlib-meta"><code>isPointInCircle(lat, lon, centerLat, centerLon, radiusKm) → boolean</code> · <a href="/reference/stdlib#geospatial">Geospatial</a></p>

Checks if a point is within a circular radius from a center point.

- `lat` (required): point latitude

- `lon` (required): point longitude

- `centerLat` (required): circle center latitude

- `centerLon` (required): circle center longitude

- `radiusKm` (required): radius in kilometers

``` utlx
{
  inRange: isPointInCircle($input.lat, $input.lon, 52.3676, 4.9041, 10),
  nearStore: isPointInCircle($input.userLat, $input.userLon, $input.storeLat, $input.storeLon, 5)
}
```

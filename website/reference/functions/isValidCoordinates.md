---
title: isValidCoordinates
description: "isValidCoordinates — UTL-X Geospatial function. Checks if coordinates are valid (latitude -90 to 90, longitude -180 to"
pageClass: stdlib-page
---

# isValidCoordinates

<p class="stdlib-meta"><code>isValidCoordinates(lat, lon) → boolean</code> · <a href="/reference/stdlib#geospatial">Geospatial</a></p>

Checks if coordinates are valid (latitude -90 to 90, longitude -180 to
180).

- `lat` (required): latitude value

- `lon` (required): longitude value

``` utlx
{
  valid: isValidCoordinates($input.lat, $input.lon)
}
```

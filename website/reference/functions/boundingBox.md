---
title: boundingBox
description: "boundingBox — UTL-X Geospatial function. Calculate the bounding box (min/max latitude and longitude) for an array"
pageClass: stdlib-page
---

# boundingBox

<p class="stdlib-meta"><code>boundingBox(coordinates) → object</code> · <a href="/reference/stdlib#geospatial">Geospatial</a></p>

Calculate the bounding box (min/max latitude and longitude) for an array
of coordinates.

- `coordinates` (required): array of `{lat, lon}` objects

``` bash
echo '{"points": [{"lat": 52.37, "lon": 4.90}, {"lat": 48.86, "lon": 2.35}, {"lat": 51.51, "lon": -0.13}]}' \
  | utlx -e 'boundingBox($input.points)'
# {"minLat": 48.86, "maxLat": 52.37, "minLon": -0.13, "maxLon": 4.90}
```

``` utlx
{
  bounds: boundingBox($input.locations)
}
```

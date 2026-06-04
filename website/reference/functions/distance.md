---
title: distance
description: "distance — UTL-X Geospatial function. Calculate the distance in kilometers between two geographic coordinates"
pageClass: stdlib-page
---

# distance

<p class="stdlib-meta"><code>distance(lat1, lon1, lat2, lon2) → number</code> · <a href="/reference/stdlib#geospatial">Geospatial</a></p>

Calculate the distance in kilometers between two geographic coordinates
using the Haversine formula.

- `lat1` (required): latitude of point 1

- `lon1` (required): longitude of point 1

- `lat2` (required): latitude of point 2

- `lon2` (required): longitude of point 2

``` bash
echo '{"from": {"lat": 52.37, "lon": 4.90}, "to": {"lat": 48.86, "lon": 2.35}}' \
  | utlx -e 'distance($input.from.lat, $input.from.lon, $input.to.lat, $input.to.lon)'
# 430.5 (approximately — Amsterdam to Paris in km)
```

``` utlx
{
  distanceKm: distance($input.origin.lat, $input.origin.lon, $input.dest.lat, $input.dest.lon)
}
```

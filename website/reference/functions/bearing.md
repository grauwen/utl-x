---
title: bearing
description: "bearing — UTL-X Geospatial function. Calculate the initial bearing (forward azimuth) from point1 to point2 in"
pageClass: stdlib-page
---

# bearing

<p class="stdlib-meta"><code>bearing(lat1, lon1, lat2, lon2) → number</code> · <a href="/reference/stdlib#geospatial">Geospatial</a></p>

Calculate the initial bearing (forward azimuth) from point1 to point2 in
degrees (0-360).

- `lat1` (required): latitude of origin point

- `lon1` (required): longitude of origin point

- `lat2` (required): latitude of destination point

- `lon2` (required): longitude of destination point

``` bash
echo '{"from": {"lat": 52.37, "lon": 4.90}, "to": {"lat": 48.86, "lon": 2.35}}' \
  | utlx -e 'bearing($input.from.lat, $input.from.lon, $input.to.lat, $input.to.lon)'
# 210.5 (degrees — roughly southwest)
```

``` utlx
{
  heading: bearing($input.origin.lat, $input.origin.lon, $input.dest.lat, $input.dest.lon)
}
```

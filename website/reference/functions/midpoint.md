---
title: midpoint
description: "midpoint — UTL-X Geospatial function. Calculate the geographic midpoint between two coordinates using the"
pageClass: stdlib-page
---

# midpoint

<p class="stdlib-meta"><code>midpoint(lat1, lon1, lat2, lon2) → object</code> · <a href="/reference/stdlib#geospatial">Geospatial</a></p>

Calculate the geographic midpoint between two coordinates using the
Haversine formula.

- `lat1` (required): latitude of first point

- `lon1` (required): longitude of first point

- `lat2` (required): latitude of second point

- `lon2` (required): longitude of second point

``` utlx
{
  center: midpoint(37.7749, -122.4194, 34.0522, -118.2437)
  // {lat: 35.9135, lon: -120.3315}
}
```

// stdlib/src/main/kotlin/org/apache/utlx/stdlib/geo/GeospatialFunctions.kt
package org.apache.utlx.stdlib.geo

import org.apache.utlx.core.udm.UDM
import kotlin.math.*
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Geospatial Functions Module
 * 
 * UNIQUE INDUSTRY-FIRST FEATURE: Geospatial operations in a transformation language!
 * 
 * Provides location-based calculations for logistics, IoT, retail, and mapping applications.
 * All calculations use the WGS84 ellipsoid model (standard GPS coordinates).
 * 
 * Categories:
 * - Distance: Haversine distance calculations
 * - Bearing: Direction between points
 * - Geofencing: Point-in-polygon, point-in-circle
 * - Utilities: Midpoint, destination point
 * 
 * Use Cases:
 * - Logistics: Calculate shipping distances
 * - IoT: Geofencing for asset tracking
 * - Retail: Store locator "within X miles"
 * - Analytics: Geographic data enrichment
 * 
 * @since UTL-X 1.2
 */
object GeospatialFunctions {
    
    // Earth radius constants
    private const val EARTH_RADIUS_KM = 6371.0
    private const val EARTH_RADIUS_MI = 3959.0
    private const val EARTH_RADIUS_M = 6371000.0
    private const val EARTH_RADIUS_NM = 3440.0 // Nautical miles
    
    // ============================================
    // DISTANCE CALCULATIONS
    // ============================================
    
    @UTLXFunction(
        description = "Calculates the distance between two geographic coordinates using the Haversine formula.",
        minArgs = 4,
        maxArgs = 4,
        category = "Geospatial",
        returns = "Result of the operation",
        example = "distance(...) => result",
        notes = "The Haversine formula determines the great-circle distance between two points\non a sphere given their longitudes and latitudes.\nCoordinates should be in decimal degrees:\n- Latitude: -90 (South) to +90 (North)\n- Longitude: -180 (West) to +180 (East)\n[1] longitude1 (Number, decimal degrees)\n[2] latitude2 (Number, decimal degrees)\n[3] longitude2 (Number, decimal degrees)\n[4] unit (String, optional: \"km\", \"mi\", \"m\", \"nm\", default: \"km\")\nExample:\n```\n// New York to London\ndistance(40.7128, -74.0060, 51.5074, -0.1278)\n→ 5570.22 (km)\ndistance(40.7128, -74.0060, 51.5074, -0.1278, \"mi\")\n→ 3461.34 (miles)\n// San Francisco to Los Angeles\ndistance(37.7749, -122.4194, 34.0522, -118.2437, \"mi\")\n→ 347.38\n// Short distance in meters\ndistance(40.7589, -73.9851, 40.7614, -73.9776, \"m\")\n→ 623.45\n```",
        tags = ["geospatial"],
        since = "1.0"
    )
    /**
     * Calculates the distance between two geographic coordinates using the Haversine formula.
     * 
     * The Haversine formula determines the great-circle distance between two points
     * on a sphere given their longitudes and latitudes.
     * 
     * Coordinates should be in decimal degrees:
     * - Latitude: -90 (South) to +90 (North)
     * - Longitude: -180 (West) to +180 (East)
     * 
     * @param args [0] latitude1 (Number, decimal degrees)
     *             [1] longitude1 (Number, decimal degrees)
     *             [2] latitude2 (Number, decimal degrees)
     *             [3] longitude2 (Number, decimal degrees)
     *             [4] unit (String, optional: "km", "mi", "m", "nm", default: "km")
     * @return distance in specified units
     * 
     * Example:
     * ```
     * // New York to London
     * distance(40.7128, -74.0060, 51.5074, -0.1278)
     * → 5570.22 (km)
     * 
     * distance(40.7128, -74.0060, 51.5074, -0.1278, "mi")
     * → 3461.34 (miles)
     * 
     * // San Francisco to Los Angeles
     * distance(37.7749, -122.4194, 34.0522, -118.2437, "mi")
     * → 347.38
     * 
     * // Short distance in meters
     * distance(40.7589, -73.9851, 40.7614, -73.9776, "m")
     * → 623.45
     * ```
     */
    fun distance(args: List<UDM>): UDM {
        // Support both array-based and flat argument styles
        val (lat1Val, lon1Val, lat2Val, lon2Val, unit) = when {
            // Array style: distance([lat1, lon1], [lat2, lon2]) or distance([lat1, lon1], [lat2, lon2], unit)
            args.size >= 2 && args[0] is UDM.Array && args[1] is UDM.Array -> {
                val coord1 = args[0] as UDM.Array
                val coord2 = args[1] as UDM.Array
                if (coord1.elements.size != 2 || coord2.elements.size != 2) {
                    throw IllegalArgumentException("Coordinate arrays must contain exactly 2 elements [lat, lon]")
                }
                val unitVal = if (args.size > 2) args[2].asString().lowercase() else "km"
                Tuple5(
                    coord1.elements[0].asNumber(),
                    coord1.elements[1].asNumber(),
                    coord2.elements[0].asNumber(),
                    coord2.elements[1].asNumber(),
                    unitVal
                )
            }
            // Flat style: distance(lat1, lon1, lat2, lon2) or distance(lat1, lon1, lat2, lon2, unit)
            args.size >= 4 -> {
                val unitVal = if (args.size > 4) args[4].asString().lowercase() else "km"
                Tuple5(
                    args[0].asNumber(),
                    args[1].asNumber(),
                    args[2].asNumber(),
                    args[3].asNumber(),
                    unitVal
                )
            }
            else -> throw IllegalArgumentException("distance() requires either 2 coordinate arrays [lat,lon] or 4 individual coordinates (lat1, lon1, lat2, lon2)")
        }

        val lat1 = Math.toRadians(lat1Val)
        val lon1 = Math.toRadians(lon1Val)
        val lat2 = Math.toRadians(lat2Val)
        val lon2 = Math.toRadians(lon2Val)

        // Validate coordinates
        validateCoordinates(lat1Val, lon1Val)
        validateCoordinates(lat2Val, lon2Val)

        // Haversine formula
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))

        val radius = when (unit) {
            "mi", "miles" -> EARTH_RADIUS_MI
            "m", "meters" -> EARTH_RADIUS_M
            "nm", "nautical" -> EARTH_RADIUS_NM
            else -> EARTH_RADIUS_KM
        }

        val distance = radius * c
        return UDM.Scalar(distance)
    }

    // Helper data classes for coordinate tuples
    private data class Tuple4<A, B, C, D>(val v1: A, val v2: B, val v3: C, val v4: D)
    private data class Tuple5<A, B, C, D, E>(val v1: A, val v2: B, val v3: C, val v4: D, val v5: E)
    private data class Tuple6<A, B, C, D, E, F>(val v1: A, val v2: B, val v3: C, val v4: D, val v5: E, val v6: F)
    
    @UTLXFunction(
        description = "Calculates the initial bearing (forward azimuth) from point1 to point2.",
        minArgs = 4,
        maxArgs = 4,
        category = "Geospatial",
        returns = "Result of the operation",
        example = "bearing(...) => result",
        notes = "Bearing is the compass direction in degrees (0-360):\n- 0° = North\n- 90° = East\n- 180° = South\n- 270° = West\n[1] longitude1 (Number)\n[2] latitude2 (Number)\n[3] longitude2 (Number)\nExample:\n```\n// New York to London (Northeast)\nbearing(40.7128, -74.0060, 51.5074, -0.1278)\n→ 51.36° (Northeast)\n// Los Angeles to Tokyo (West-Northwest)\nbearing(34.0522, -118.2437, 35.6762, 139.6503)\n→ 298.45° (Northwest)\n// Sydney to Auckland (East-Southeast)\nbearing(-33.8688, 151.2093, -36.8485, 174.7633)\n→ 108.92° (East-Southeast)\n```",
        tags = ["geospatial"],
        since = "1.0"
    )
    /**
     * Calculates the initial bearing (forward azimuth) from point1 to point2.
     * 
     * Bearing is the compass direction in degrees (0-360):
     * - 0° = North
     * - 90° = East
     * - 180° = South
     * - 270° = West
     * 
     * @param args [0] latitude1 (Number)
     *             [1] longitude1 (Number)
     *             [2] latitude2 (Number)
     *             [3] longitude2 (Number)
     * @return bearing in degrees (0-360)
     * 
     * Example:
     * ```
     * // New York to London (Northeast)
     * bearing(40.7128, -74.0060, 51.5074, -0.1278)
     * → 51.36° (Northeast)
     * 
     * // Los Angeles to Tokyo (West-Northwest)
     * bearing(34.0522, -118.2437, 35.6762, 139.6503)
     * → 298.45° (Northwest)
     * 
     * // Sydney to Auckland (East-Southeast)
     * bearing(-33.8688, 151.2093, -36.8485, 174.7633)
     * → 108.92° (East-Southeast)
     * ```
     */
    fun bearing(args: List<UDM>): UDM {
        // Support both array-based and flat argument styles
        val (lat1Val, lon1Val, lat2Val, lon2Val) = when {
            // Array style: bearing([lat1, lon1], [lat2, lon2])
            args.size >= 2 && args[0] is UDM.Array && args[1] is UDM.Array -> {
                val coord1 = args[0] as UDM.Array
                val coord2 = args[1] as UDM.Array
                if (coord1.elements.size != 2 || coord2.elements.size != 2) {
                    throw IllegalArgumentException("Coordinate arrays must contain exactly 2 elements [lat, lon]")
                }
                Tuple4(
                    coord1.elements[0].asNumber(),
                    coord1.elements[1].asNumber(),
                    coord2.elements[0].asNumber(),
                    coord2.elements[1].asNumber()
                )
            }
            // Flat style: bearing(lat1, lon1, lat2, lon2)
            args.size >= 4 -> {
                Tuple4(
                    args[0].asNumber(),
                    args[1].asNumber(),
                    args[2].asNumber(),
                    args[3].asNumber()
                )
            }
            else -> throw IllegalArgumentException("bearing() requires either 2 coordinate arrays [lat,lon] or 4 individual coordinates (lat1, lon1, lat2, lon2)")
        }

        val lat1 = Math.toRadians(lat1Val)
        val lon1 = Math.toRadians(lon1Val)
        val lat2 = Math.toRadians(lat2Val)
        val lon2 = Math.toRadians(lon2Val)

        validateCoordinates(lat1Val, lon1Val)
        validateCoordinates(lat2Val, lon2Val)
        
        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        val normalizedBearing = (bearing + 360) % 360
        
        return UDM.Scalar(normalizedBearing)
    }
    
    // ============================================
    // GEOFENCING
    // ============================================
    
    @UTLXFunction(
        description = "Checks if a point is within a circular radius from a center point.",
        minArgs = 5,
        maxArgs = 5,
        category = "Geospatial",
        parameters = [
            "array: Input array to process",
        "pointLon: Pointlon value",
        "centerLat: Centerlat value",
        "centerLon: Centerlon value",
        "radius: Radius value"
        ],
        returns = "Boolean indicating the result",
        example = "isPointInCircle(...) => result",
        notes = "Useful for geofencing applications like \"show stores within 10 miles\"\nor \"alert when asset leaves 5km zone\".\n[1] point longitude (Number)\n[2] center latitude (Number)\n[3] center longitude (Number)\n[4] radius (Number)\n[5] unit (String, optional: \"km\", \"mi\", \"m\", default: \"km\")\nExample:\n```\n// Is this location within 10km of downtown?\nisPointInCircle(37.7749, -122.4194, 37.7849, -122.4094, 10)\n→ true\n// Store locator: within 5 miles?\nisPointInCircle(40.7589, -73.9851, 40.7128, -74.0060, 5, \"mi\")\n→ false (too far)\n// Asset tracking: within 100m safety zone?\nisPointInCircle(51.5074, -0.1278, 51.5076, -0.1280, 100, \"m\")\n→ true\n```",
        tags = ["geospatial"],
        since = "1.0"
    )
    /**
     * Checks if a point is within a circular radius from a center point.
     * 
     * Useful for geofencing applications like "show stores within 10 miles"
     * or "alert when asset leaves 5km zone".
     * 
     * @param args [0] point latitude (Number)
     *             [1] point longitude (Number)
     *             [2] center latitude (Number)
     *             [3] center longitude (Number)
     *             [4] radius (Number)
     *             [5] unit (String, optional: "km", "mi", "m", default: "km")
     * @return true if point is within circle, false otherwise
     * 
     * Example:
     * ```
     * // Is this location within 10km of downtown?
     * isPointInCircle(37.7749, -122.4194, 37.7849, -122.4094, 10)
     * → true
     * 
     * // Store locator: within 5 miles?
     * isPointInCircle(40.7589, -73.9851, 40.7128, -74.0060, 5, "mi")
     * → false (too far)
     * 
     * // Asset tracking: within 100m safety zone?
     * isPointInCircle(51.5074, -0.1278, 51.5076, -0.1280, 100, "m")
     * → true
     * ```
     */
    fun isPointInCircle(args: List<UDM>): UDM {
        // Support both array-based and flat argument styles
        val (pointLat, pointLon, centerLat, centerLon, radius, unit) = when {
            // Array style: isPointInCircle([lat, lon], [centerLat, centerLon], radius) or with unit
            args.size >= 3 && args[0] is UDM.Array && args[1] is UDM.Array -> {
                val point = args[0] as UDM.Array
                val center = args[1] as UDM.Array
                if (point.elements.size != 2 || center.elements.size != 2) {
                    throw IllegalArgumentException("Coordinate arrays must contain exactly 2 elements [lat, lon]")
                }
                val radiusVal = args[2].asNumber()
                val unitVal = if (args.size > 3) args[3].asString() else "km"
                Tuple6(
                    point.elements[0].asNumber(),
                    point.elements[1].asNumber(),
                    center.elements[0].asNumber(),
                    center.elements[1].asNumber(),
                    radiusVal,
                    unitVal
                )
            }
            // Flat style: isPointInCircle(pointLat, pointLon, centerLat, centerLon, radius) or with unit
            args.size >= 5 -> {
                val unitVal = if (args.size > 5) args[5].asString() else "km"
                Tuple6(
                    args[0].asNumber(),
                    args[1].asNumber(),
                    args[2].asNumber(),
                    args[3].asNumber(),
                    args[4].asNumber(),
                    unitVal
                )
            }
            else -> throw IllegalArgumentException("isPointInCircle() requires either 3 arguments (point_array, center_array, radius) or 5 arguments (pointLat, pointLon, centerLat, centerLon, radius)")
        }

        if (radius < 0) {
            throw IllegalArgumentException("Radius must be non-negative, got: $radius")
        }

        val dist = distance(listOf(
            UDM.Scalar(pointLat), UDM.Scalar(pointLon),
            UDM.Scalar(centerLat), UDM.Scalar(centerLon),
            UDM.Scalar(unit)
        )).asNumber()

        return UDM.Scalar(dist <= radius)
    }
    
    @UTLXFunction(
        description = "Checks if a point is inside a polygon using the ray casting algorithm.",
        minArgs = 3,
        maxArgs = 3,
        category = "Geospatial",
        parameters = [
            "array: Input array to process",
        "pointLon: Pointlon value",
        "polygonArg: Polygonarg value"
        ],
        returns = "Boolean indicating the result",
        example = "isPointInPolygon(...) => result",
        notes = "The polygon is defined as an array of coordinate pairs [lat, lon].\nUses the \"even-odd rule\" (ray casting) algorithm.\n[1] point longitude (Number)\n[2] polygon vertices (Array of [lat, lon] arrays)\nExample:\n```\n// Is point inside this delivery zone?\nisPointInPolygon(\n40.7128, -74.0060,\n[[40.7, -74.1], [40.8, -74.1], [40.8, -74.0], [40.7, -74.0]]\n)\n→ true\n// Geofencing: inside city boundary?\nisPointInPolygon(\n51.5074, -0.1278,\n[[51.5, -0.2], [51.6, -0.2], [51.6, -0.05], [51.5, -0.05]]\n)\n→ true\n```",
        tags = ["geospatial"],
        since = "1.0"
    )
    /**
     * Checks if a point is inside a polygon using the ray casting algorithm.
     * 
     * The polygon is defined as an array of coordinate pairs [lat, lon].
     * Uses the "even-odd rule" (ray casting) algorithm.
     * 
     * @param args [0] point latitude (Number)
     *             [1] point longitude (Number)
     *             [2] polygon vertices (Array of [lat, lon] arrays)
     * @return true if point is inside polygon, false otherwise
     * 
     * Example:
     * ```
     * // Is point inside this delivery zone?
     * isPointInPolygon(
     *   40.7128, -74.0060,
     *   [[40.7, -74.1], [40.8, -74.1], [40.8, -74.0], [40.7, -74.0]]
     * )
     * → true
     * 
     * // Geofencing: inside city boundary?
     * isPointInPolygon(
     *   51.5074, -0.1278,
     *   [[51.5, -0.2], [51.6, -0.2], [51.6, -0.05], [51.5, -0.05]]
     * )
     * → true
     * ```
     */
    fun isPointInPolygon(args: List<UDM>): UDM {
        // Support both array-based and flat argument styles
        val (pointLat, pointLon, polygonArg) = when {
            // Array style: isPointInPolygon([lat, lon], polygon)
            args.size >= 2 && args[0] is UDM.Array && (args[0] as UDM.Array).elements.size == 2 -> {
                val point = args[0] as UDM.Array
                Triple(
                    point.elements[0].asNumber(),
                    point.elements[1].asNumber(),
                    args[1]
                )
            }
            // Flat style: isPointInPolygon(lat, lon, polygon)
            args.size >= 3 -> {
                Triple(
                    args[0].asNumber(),
                    args[1].asNumber(),
                    args[2]
                )
            }
            else -> throw IllegalArgumentException("isPointInPolygon() requires either 2 arguments (point_array, polygon) or 3 arguments (pointLat, pointLon, polygon)")
        }
        
        if (polygonArg !is UDM.Array) {
            throw IllegalArgumentException("Polygon must be an array of coordinate pairs")
        }
        
        // Extract polygon vertices
        val vertices = polygonArg.elements.map { vertex ->
            if (vertex !is UDM.Array || vertex.elements.size < 2) {
                throw IllegalArgumentException("Each vertex must be [lat, lon] array")
            }
            Pair(
                vertex.elements[0].asNumber(),
                vertex.elements[1].asNumber()
            )
        }
        
        if (vertices.size < 3) {
            throw IllegalArgumentException("Polygon must have at least 3 vertices")
        }
        
        // Ray casting algorithm
        var inside = false
        var j = vertices.size - 1
        
        for (i in vertices.indices) {
            val (xi, yi) = vertices[i]
            val (xj, yj) = vertices[j]
            
            val intersect = ((yi > pointLon) != (yj > pointLon)) &&
                           (pointLat < (xj - xi) * (pointLon - yi) / (yj - yi) + xi)
            
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        
        return UDM.Scalar(inside)
    }
    
    // ============================================
    // CALCULATIONS & UTILITIES
    // ============================================
    
    @UTLXFunction(
        description = "Calculates the midpoint between two coordinates.",
        minArgs = 4,
        maxArgs = 4,
        category = "Geospatial",
        returns = "the geographic center point (approximate for long distances).",
        example = "midpoint(...) => result",
        notes = "Returns the geographic center point (approximate for long distances).\n[1] longitude1 (Number)\n[2] latitude2 (Number)\n[3] longitude2 (Number)\nExample:\n```\n// Midpoint between New York and London\nmidpoint(40.7128, -74.0060, 51.5074, -0.1278)\n→ {lat: 51.3826, lon: -42.0609}\n// Meeting point between two cities\nmidpoint(37.7749, -122.4194, 34.0522, -118.2437)\n→ {lat: 35.9135, lon: -120.3315}\n```",
        tags = ["geospatial"],
        since = "1.0"
    )
    /**
     * Calculates the midpoint between two coordinates.
     * 
     * Returns the geographic center point (approximate for long distances).
     * 
     * @param args [0] latitude1 (Number)
     *             [1] longitude1 (Number)
     *             [2] latitude2 (Number)
     *             [3] longitude2 (Number)
     * @return object with {lat, lon} of midpoint
     * 
     * Example:
     * ```
     * // Midpoint between New York and London
     * midpoint(40.7128, -74.0060, 51.5074, -0.1278)
     * → {lat: 51.3826, lon: -42.0609}
     * 
     * // Meeting point between two cities
     * midpoint(37.7749, -122.4194, 34.0522, -118.2437)
     * → {lat: 35.9135, lon: -120.3315}
     * ```
     */
    fun midpoint(args: List<UDM>): UDM {
        // Support both array-based and flat argument styles
        val (lat1Val, lon1Val, lat2Val, lon2Val) = when {
            // Array style: midpoint([lat1, lon1], [lat2, lon2])
            args.size >= 2 && args[0] is UDM.Array && args[1] is UDM.Array -> {
                val coord1 = args[0] as UDM.Array
                val coord2 = args[1] as UDM.Array
                if (coord1.elements.size != 2 || coord2.elements.size != 2) {
                    throw IllegalArgumentException("Coordinate arrays must contain exactly 2 elements [lat, lon]")
                }
                Tuple4(
                    coord1.elements[0].asNumber(),
                    coord1.elements[1].asNumber(),
                    coord2.elements[0].asNumber(),
                    coord2.elements[1].asNumber()
                )
            }
            // Flat style: midpoint(lat1, lon1, lat2, lon2)
            args.size >= 4 -> {
                Tuple4(
                    args[0].asNumber(),
                    args[1].asNumber(),
                    args[2].asNumber(),
                    args[3].asNumber()
                )
            }
            else -> throw IllegalArgumentException("midpoint() requires either 2 coordinate arrays [lat,lon] or 4 individual coordinates (lat1, lon1, lat2, lon2)")
        }

        val lat1 = Math.toRadians(lat1Val)
        val lon1 = Math.toRadians(lon1Val)
        val lat2 = Math.toRadians(lat2Val)
        val lon2 = Math.toRadians(lon2Val)

        validateCoordinates(lat1Val, lon1Val)
        validateCoordinates(lat2Val, lon2Val)
        
        val dLon = lon2 - lon1
        
        val bx = cos(lat2) * cos(dLon)
        val by = cos(lat2) * sin(dLon)
        
        val lat3 = atan2(
            sin(lat1) + sin(lat2),
            sqrt((cos(lat1) + bx).pow(2) + by.pow(2))
        )
        val lon3 = lon1 + atan2(by, cos(lat1) + bx)

        return UDM.Array(listOf(
            UDM.Scalar(Math.toDegrees(lat3)),
            UDM.Scalar(Math.toDegrees(lon3))
        ))
    }
    
    @UTLXFunction(
        description = "Calculates a destination point given a starting point, bearing, and distance.",
        minArgs = 4,
        maxArgs = 4,
        category = "Geospatial",
        parameters = [
            "array: Input array to process",
        "bearingDeg: Bearingdeg value",
        "distance: Distance value"
        ],
        returns = "Result of the operation",
        example = "destinationPoint(...) => result",
        notes = "Useful for \"travel X km in direction Y\" calculations.\n[1] starting longitude (Number)\n[2] bearing in degrees (Number, 0-360)\n[3] distance (Number)\n[4] unit (String, optional: \"km\", \"mi\", \"m\", default: \"km\")\nExample:\n```\n// Travel 100km north from starting point\ndestinationPoint(40.7128, -74.0060, 0, 100)\n→ {lat: 41.6128, lon: -74.0060}\n// Travel 50 miles east\ndestinationPoint(34.0522, -118.2437, 90, 50, \"mi\")\n→ {lat: 34.0522, lon: -117.5237}\n```",
        tags = ["geospatial"],
        since = "1.0"
    )
    /**
     * Calculates a destination point given a starting point, bearing, and distance.
     * 
     * Useful for "travel X km in direction Y" calculations.
     * 
     * @param args [0] starting latitude (Number)
     *             [1] starting longitude (Number)
     *             [2] bearing in degrees (Number, 0-360)
     *             [3] distance (Number)
     *             [4] unit (String, optional: "km", "mi", "m", default: "km")
     * @return object with {lat, lon} of destination
     * 
     * Example:
     * ```
     * // Travel 100km north from starting point
     * destinationPoint(40.7128, -74.0060, 0, 100)
     * → {lat: 41.6128, lon: -74.0060}
     * 
     * // Travel 50 miles east
     * destinationPoint(34.0522, -118.2437, 90, 50, "mi")
     * → {lat: 34.0522, lon: -117.5237}
     * ```
     */
    fun destinationPoint(args: List<UDM>): UDM {
        // Support both array-based and flat argument styles
        val (latVal, lonVal, distance, bearingDeg, unit) = when {
            // Array style: destinationPoint([lat, lon], distance, bearing) or with unit
            args.size >= 3 && args[0] is UDM.Array -> {
                val point = args[0] as UDM.Array
                if (point.elements.size != 2) {
                    throw IllegalArgumentException("Coordinate array must contain exactly 2 elements [lat, lon]")
                }
                val distVal = args[1].asNumber()
                val bearingVal = args[2].asNumber()
                val unitVal = if (args.size > 3) args[3].asString().lowercase() else "km"
                Tuple5(
                    point.elements[0].asNumber(),
                    point.elements[1].asNumber(),
                    distVal,
                    bearingVal,
                    unitVal
                )
            }
            // Flat style: destinationPoint(lat, lon, distance, bearing) or with unit
            args.size >= 4 -> {
                val unitVal = if (args.size > 4) args[4].asString().lowercase() else "km"
                Tuple5(
                    args[0].asNumber(),
                    args[1].asNumber(),
                    args[2].asNumber(),
                    args[3].asNumber(),
                    unitVal
                )
            }
            else -> throw IllegalArgumentException("destinationPoint() requires either 3 arguments (point_array, distance, bearing) or 4 arguments (lat, lon, distance, bearing)")
        }

        if (bearingDeg < 0 || bearingDeg > 360) {
            throw IllegalArgumentException("Bearing must be between 0 and 360 degrees, got: $bearingDeg")
        }

        val lat = Math.toRadians(latVal)
        val lon = Math.toRadians(lonVal)

        validateCoordinates(latVal, lonVal)
        
        val radius = when (unit) {
            "mi", "miles" -> EARTH_RADIUS_MI
            "m", "meters" -> EARTH_RADIUS_M
            "nm", "nautical" -> EARTH_RADIUS_NM
            else -> EARTH_RADIUS_KM
        }
        
        val bearing = Math.toRadians(bearingDeg)
        val angularDistance = distance / radius
        
        val lat2 = asin(
            sin(lat) * cos(angularDistance) +
            cos(lat) * sin(angularDistance) * cos(bearing)
        )
        
        val lon2 = lon + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat),
            cos(angularDistance) - sin(lat) * sin(lat2)
        )
        
        return UDM.Array(listOf(
            UDM.Scalar(Math.toDegrees(lat2)),
            UDM.Scalar(Math.toDegrees(lon2))
        ))
    }
    
    @UTLXFunction(
        description = "Calculates the bounding box (min/max lat/lon) for an array of coordinates.",
        minArgs = 1,
        maxArgs = 1,
        category = "Geospatial",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "boundingBox(...) => result",
        notes = "Useful for map viewport calculations or data validation.\nExample:\n```\nboundingBox([\n{lat: 40.7128, lon: -74.0060},\n{lat: 51.5074, lon: -0.1278},\n{lat: 35.6762, lon: 139.6503}\n])\n→ {\nminLat: 35.6762, maxLat: 51.5074,\nminLon: -74.0060, maxLon: 139.6503\n}\n```",
        tags = ["geospatial"],
        since = "1.0"
    )
    /**
     * Calculates the bounding box (min/max lat/lon) for an array of coordinates.
     * 
     * Useful for map viewport calculations or data validation.
     * 
     * @param args [0] array of coordinate objects with {lat, lon}
     * @return object with {minLat, maxLat, minLon, maxLon}
     * 
     * Example:
     * ```
     * boundingBox([
     *   {lat: 40.7128, lon: -74.0060},
     *   {lat: 51.5074, lon: -0.1278},
     *   {lat: 35.6762, lon: 139.6503}
     * ])
     * → {
     *     minLat: 35.6762, maxLat: 51.5074,
     *     minLon: -74.0060, maxLon: 139.6503
     *   }
     * ```
     */
    fun boundingBox(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("boundingBox() requires 1 argument: array of coordinates")
        }
        
        val coords = args[0]
        if (coords !is UDM.Array || coords.elements.isEmpty()) {
            throw IllegalArgumentException("Argument must be non-empty array of coordinates")
        }
        
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        
        coords.elements.forEach { coord ->
            val (lat, lon) = when (coord) {
                is UDM.Array -> {
                    if (coord.elements.size != 2) {
                        throw IllegalArgumentException("Each coordinate array must contain exactly 2 elements [lat, lon]")
                    }
                    Pair(
                        coord.elements[0].asNumber(),
                        coord.elements[1].asNumber()
                    )
                }
                is UDM.Object -> {
                    val latVal = coord.properties["lat"]?.asNumber()
                        ?: throw IllegalArgumentException("Missing lat property in coordinate object")
                    val lonVal = coord.properties["lon"]?.asNumber()
                        ?: throw IllegalArgumentException("Missing lon property in coordinate object")
                    Pair(latVal, lonVal)
                }
                else -> throw IllegalArgumentException("Each coordinate must be either an array [lat, lon] or an object {lat, lon}")
            }

            minLat = minOf(minLat, lat)
            maxLat = maxOf(maxLat, lat)
            minLon = minOf(minLon, lon)
            maxLon = maxOf(maxLon, lon)
        }
        
        return UDM.Object(mutableMapOf(
            "minLat" to UDM.Scalar(minLat),
            "maxLat" to UDM.Scalar(maxLat),
            "minLon" to UDM.Scalar(minLon),
            "maxLon" to UDM.Scalar(maxLon)
        ))
    }
    
    // ============================================
    // VALIDATION
    // ============================================
    
    /**
     * Validates latitude and longitude coordinates.
     * 
     * @throws IllegalArgumentException if coordinates are out of range
     */
    private fun validateCoordinates(lat: Double, lon: Double) {
        if (lat < -90 || lat > 90) {
            throw IllegalArgumentException("Latitude must be between -90 and 90, got: $lat")
        }
        if (lon < -180 || lon > 180) {
            throw IllegalArgumentException("Longitude must be between -180 and 180, got: $lon")
        }
    }
    
    @UTLXFunction(
        description = "Checks if coordinates are valid.",
        minArgs = 2,
        maxArgs = 2,
        category = "Geospatial",
        parameters = [
            "lat: Lat value",
        "lon: Lon value"
        ],
        returns = "Boolean indicating the result",
        example = "isValidCoordinates(...) => result",
        notes = "[1] longitude (Number)\nExample:\n```\nisValidCoordinates(40.7128, -74.0060) → true\nisValidCoordinates(100, 0) → false (lat out of range)\nisValidCoordinates(0, 200) → false (lon out of range)\n```",
        tags = ["geospatial"],
        since = "1.0"
    )
    /**
     * Checks if coordinates are valid.
     * 
     * @param args [0] latitude (Number)
     *             [1] longitude (Number)
     * @return true if valid, false otherwise
     * 
     * Example:
     * ```
     * isValidCoordinates(40.7128, -74.0060) → true
     * isValidCoordinates(100, 0) → false (lat out of range)
     * isValidCoordinates(0, 200) → false (lon out of range)
     * ```
     */
    fun isValidCoordinates(args: List<UDM>): UDM {
        // Support both array-based and flat argument styles
        val (lat, lon) = when {
            // Array style: isValidCoordinates([lat, lon])
            args.size >= 1 && args[0] is UDM.Array -> {
                val coord = args[0] as UDM.Array
                if (coord.elements.size != 2) {
                    throw IllegalArgumentException("Coordinate array must contain exactly 2 elements [lat, lon]")
                }
                Pair(
                    coord.elements[0].asNumber(),
                    coord.elements[1].asNumber()
                )
            }
            // Flat style: isValidCoordinates(lat, lon)
            args.size >= 2 -> {
                Pair(
                    args[0].asNumber(),
                    args[1].asNumber()
                )
            }
            else -> throw IllegalArgumentException("isValidCoordinates() requires either 1 coordinate array [lat,lon] or 2 individual coordinates (lat, lon)")
        }

        val valid = lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180
        return UDM.Scalar(valid)
    }
}

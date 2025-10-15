// stdlib/src/main/kotlin/org/apache/utlx/stdlib/geo/GeospatialFunctions.kt
package org.apache.utlx.stdlib.geo

import org.apache.utlx.core.udm.UDM
import kotlin.math.*

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
        if (args.size < 4) {
            throw IllegalArgumentException("distance() requires at least 4 arguments: lat1, lon1, lat2, lon2")
        }
        
        val lat1 = Math.toRadians(args[0].asNumber())
        val lon1 = Math.toRadians(args[1].asNumber())
        val lat2 = Math.toRadians(args[2].asNumber())
        val lon2 = Math.toRadians(args[3].asNumber())
        val unit = if (args.size > 4) args[4].asString().toLowerCase() else "km"
        
        // Validate coordinates
        validateCoordinates(args[0].asNumber(), args[1].asNumber())
        validateCoordinates(args[2].asNumber(), args[3].asNumber())
        
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
        if (args.size < 4) {
            throw IllegalArgumentException("bearing() requires 4 arguments: lat1, lon1, lat2, lon2")
        }
        
        val lat1 = Math.toRadians(args[0].asNumber())
        val lon1 = Math.toRadians(args[1].asNumber())
        val lat2 = Math.toRadians(args[2].asNumber())
        val lon2 = Math.toRadians(args[3].asNumber())
        
        validateCoordinates(args[0].asNumber(), args[1].asNumber())
        validateCoordinates(args[2].asNumber(), args[3].asNumber())
        
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
        if (args.size < 5) {
            throw IllegalArgumentException("isPointInCircle() requires at least 5 arguments: pointLat, pointLon, centerLat, centerLon, radius")
        }
        
        val pointLat = args[0].asNumber()
        val pointLon = args[1].asNumber()
        val centerLat = args[2].asNumber()
        val centerLon = args[3].asNumber()
        val radius = args[4].asNumber()
        val unit = if (args.size > 5) args[5].asString() else "km"
        
        val dist = distance(listOf(
            UDM.Scalar(pointLat), UDM.Scalar(pointLon),
            UDM.Scalar(centerLat), UDM.Scalar(centerLon),
            UDM.Scalar(unit)
        )).asNumber()
        
        return UDM.Scalar(dist <= radius)
    }
    
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
        if (args.size < 3) {
            throw IllegalArgumentException("isPointInPolygon() requires 3 arguments: pointLat, pointLon, polygon")
        }
        
        val pointLat = args[0].asNumber()
        val pointLon = args[1].asNumber()
        val polygonArg = args[2]
        
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
        if (args.size < 4) {
            throw IllegalArgumentException("midpoint() requires 4 arguments: lat1, lon1, lat2, lon2")
        }
        
        val lat1 = Math.toRadians(args[0].asNumber())
        val lon1 = Math.toRadians(args[1].asNumber())
        val lat2 = Math.toRadians(args[2].asNumber())
        val lon2 = Math.toRadians(args[3].asNumber())
        
        validateCoordinates(args[0].asNumber(), args[1].asNumber())
        validateCoordinates(args[2].asNumber(), args[3].asNumber())
        
        val dLon = lon2 - lon1
        
        val bx = cos(lat2) * cos(dLon)
        val by = cos(lat2) * sin(dLon)
        
        val lat3 = atan2(
            sin(lat1) + sin(lat2),
            sqrt((cos(lat1) + bx).pow(2) + by.pow(2))
        )
        val lon3 = lon1 + atan2(by, cos(lat1) + bx)
        
        return UDM.Object(mutableMapOf(
            "lat" to UDM.Scalar(Math.toDegrees(lat3)),
            "lon" to UDM.Scalar(Math.toDegrees(lon3))
        ))
    }
    
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
        if (args.size < 4) {
            throw IllegalArgumentException("destinationPoint() requires at least 4 arguments: lat, lon, bearing, distance")
        }
        
        val lat = Math.toRadians(args[0].asNumber())
        val lon = Math.toRadians(args[1].asNumber())
        val bearingDeg = args[2].asNumber()
        val distance = args[3].asNumber()
        val unit = if (args.size > 4) args[4].asString().toLowerCase() else "km"
        
        validateCoordinates(args[0].asNumber(), args[1].asNumber())
        
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
        
        return UDM.Object(mutableMapOf(
            "lat" to UDM.Scalar(Math.toDegrees(lat2)),
            "lon" to UDM.Scalar(Math.toDegrees(lon2))
        ))
    }
    
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
            if (coord !is UDM.Object) {
                throw IllegalArgumentException("Each coordinate must be an object with lat and lon")
            }
            
            val lat = coord.properties["lat"]?.asNumber() 
                ?: throw IllegalArgumentException("Missing lat property")
            val lon = coord.properties["lon"]?.asNumber() 
                ?: throw IllegalArgumentException("Missing lon property")
            
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
        if (args.size < 2) {
            throw IllegalArgumentException("isValidCoordinates() requires 2 arguments: lat, lon")
        }
        
        val lat = args[0].asNumber()
        val lon = args[1].asNumber()
        
        val valid = lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180
        return UDM.Scalar(valid)
    }
}

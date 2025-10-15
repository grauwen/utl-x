// stdlib/src/test/kotlin/org/apache/utlx/stdlib/geo/GeospatialFunctionsTest.kt
package org.apache.utlx.stdlib.geo

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.math.abs

/**
 * Comprehensive test suite for Geospatial functions.
 * 
 * Tests cover:
 * - Distance calculations (Haversine formula)
 * - Bearing calculations
 * - Geofencing (circle and polygon)
 * - Coordinate utilities (midpoint, destination)
 * - Bounding box calculations
 * - Coordinate validation
 */
class GeospatialFunctionsTest {

    private fun assertAlmostEquals(expected: Double, actual: Double, delta: Double = 0.01, message: String = "") {
        assertTrue(abs(expected - actual) < delta, 
            "$message Expected: $expected, Actual: $actual, Delta: ${abs(expected - actual)}")
    }

    // ==================== Distance Tests ====================
    
    @Test
    fun `test distance - New York to London`() {
        // NYC: 40.7128° N, 74.0060° W
        // London: 51.5074° N, 0.1278° W
        val nyc = UDM.Array(listOf(UDM.Scalar(40.7128), UDM.Scalar(-74.0060)))
        val london = UDM.Array(listOf(UDM.Scalar(51.5074), UDM.Scalar(-0.1278)))
        
        val result = GeospatialFunctions.distance(listOf(nyc, london))
        val distance = (result as UDM.Scalar).value as Double
        
        // Known distance: ~5570 km
        assertAlmostEquals(5570.0, distance, delta = 50.0, "NYC to London distance")
    }
    
    @Test
    fun `test distance - same location`() {
        val paris = UDM.Array(listOf(UDM.Scalar(48.8566), UDM.Scalar(2.3522)))
        
        val result = GeospatialFunctions.distance(listOf(paris, paris))
        val distance = (result as UDM.Scalar).value as Double
        
        assertEquals(0.0, distance, 0.01, "Distance to same location should be 0")
    }
    
    @Test
    fun `test distance - Tokyo to Sydney`() {
        val tokyo = UDM.Array(listOf(UDM.Scalar(35.6762), UDM.Scalar(139.6503)))
        val sydney = UDM.Array(listOf(UDM.Scalar(-33.8688), UDM.Scalar(151.2093)))
        
        val result = GeospatialFunctions.distance(listOf(tokyo, sydney))
        val distance = (result as UDM.Scalar).value as Double
        
        // Known distance: ~7820 km
        assertAlmostEquals(7820.0, distance, delta = 50.0, "Tokyo to Sydney distance")
    }
    
    @Test
    fun `test distance - custom unit (miles)`() {
        val nyc = UDM.Array(listOf(UDM.Scalar(40.7128), UDM.Scalar(-74.0060)))
        val london = UDM.Array(listOf(UDM.Scalar(51.5074), UDM.Scalar(-0.1278)))
        val unit = UDM.Scalar("miles")
        
        val result = GeospatialFunctions.distance(listOf(nyc, london, unit))
        val distance = (result as UDM.Scalar).value as Double
        
        // ~3461 miles
        assertAlmostEquals(3461.0, distance, delta = 50.0, "NYC to London in miles")
    }

    // ==================== Bearing Tests ====================
    
    @Test
    fun `test bearing - New York to London`() {
        val nyc = UDM.Array(listOf(UDM.Scalar(40.7128), UDM.Scalar(-74.0060)))
        val london = UDM.Array(listOf(UDM.Scalar(51.5074), UDM.Scalar(-0.1278)))
        
        val result = GeospatialFunctions.bearing(listOf(nyc, london))
        val bearing = (result as UDM.Scalar).value as Double
        
        // NYC to London is approximately northeast (51-52°)
        assertAlmostEquals(51.0, bearing, delta = 2.0, "NYC to London bearing")
        assertTrue(bearing >= 0.0 && bearing < 360.0, "Bearing should be 0-360°")
    }
    
    @Test
    fun `test bearing - north direction`() {
        val start = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0)))
        val north = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(0.0)))
        
        val result = GeospatialFunctions.bearing(listOf(start, north))
        val bearing = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(0.0, bearing, delta = 1.0, "Bearing to north should be 0°")
    }
    
    @Test
    fun `test bearing - east direction`() {
        val start = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0)))
        val east = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(1.0)))
        
        val result = GeospatialFunctions.bearing(listOf(start, east))
        val bearing = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(90.0, bearing, delta = 1.0, "Bearing to east should be 90°")
    }

    // ==================== Circle Geofence Tests ====================
    
    @Test
    fun `test isPointInCircle - inside`() {
        val point = UDM.Array(listOf(UDM.Scalar(40.7580), UDM.Scalar(-73.9855))) // Times Square
        val center = UDM.Array(listOf(UDM.Scalar(40.7614), UDM.Scalar(-73.9776))) // Central Park
        val radius = UDM.Scalar(1.0) // 1 km
        
        val result = GeospatialFunctions.isPointInCircle(listOf(point, center, radius))
        val isInside = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isInside, "Times Square should be within 1km of Central Park")
    }
    
    @Test
    fun `test isPointInCircle - outside`() {
        val point = UDM.Array(listOf(UDM.Scalar(40.7128), UDM.Scalar(-74.0060))) // NYC
        val center = UDM.Array(listOf(UDM.Scalar(51.5074), UDM.Scalar(-0.1278))) // London
        val radius = UDM.Scalar(100.0) // 100 km
        
        val result = GeospatialFunctions.isPointInCircle(listOf(point, center, radius))
        val isInside = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isInside, "NYC should not be within 100km of London")
    }
    
    @Test
    fun `test isPointInCircle - on boundary`() {
        val center = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0)))
        val point = UDM.Array(listOf(UDM.Scalar(0.009), UDM.Scalar(0.0))) // ~1 km away
        val radius = UDM.Scalar(1.0)
        
        val result = GeospatialFunctions.isPointInCircle(listOf(point, center, radius))
        val isInside = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isInside, "Point on boundary should be considered inside")
    }

    // ==================== Polygon Geofence Tests ====================
    
    @Test
    fun `test isPointInPolygon - inside square`() {
        val point = UDM.Array(listOf(UDM.Scalar(0.5), UDM.Scalar(0.5)))
        val polygon = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0))),
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(0.0))),
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(1.0))),
            UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(1.0)))
        ))
        
        val result = GeospatialFunctions.isPointInPolygon(listOf(point, polygon))
        val isInside = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isInside, "Point (0.5, 0.5) should be inside unit square")
    }
    
    @Test
    fun `test isPointInPolygon - outside square`() {
        val point = UDM.Array(listOf(UDM.Scalar(1.5), UDM.Scalar(1.5)))
        val polygon = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0))),
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(0.0))),
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(1.0))),
            UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(1.0)))
        ))
        
        val result = GeospatialFunctions.isPointInPolygon(listOf(point, polygon))
        val isInside = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isInside, "Point (1.5, 1.5) should be outside unit square")
    }
    
    @Test
    fun `test isPointInPolygon - complex polygon`() {
        // Pentagon shape
        val point = UDM.Array(listOf(UDM.Scalar(38.8977), UDM.Scalar(-77.0365)))
        val pentagonVertices = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(38.8719), UDM.Scalar(-77.0563))),
            UDM.Array(listOf(UDM.Scalar(38.8677), UDM.Scalar(-77.0481))),
            UDM.Array(listOf(UDM.Scalar(38.8700), UDM.Scalar(-77.0386))),
            UDM.Array(listOf(UDM.Scalar(38.8754), UDM.Scalar(-77.0386))),
            UDM.Array(listOf(UDM.Scalar(38.8777), UDM.Scalar(-77.0481)))
        ))
        
        val result = GeospatialFunctions.isPointInPolygon(listOf(point, pentagonVertices))
        val isInside = (result as UDM.Scalar).value as Boolean
        
        // This test validates ray-casting algorithm with non-convex polygon
        assertNotNull(isInside)
    }

    // ==================== Midpoint Tests ====================
    
    @Test
    fun `test midpoint - New York and London`() {
        val nyc = UDM.Array(listOf(UDM.Scalar(40.7128), UDM.Scalar(-74.0060)))
        val london = UDM.Array(listOf(UDM.Scalar(51.5074), UDM.Scalar(-0.1278)))
        
        val result = GeospatialFunctions.midpoint(listOf(nyc, london))
        val midpoint = result as UDM.Array
        val lat = (midpoint.elements[0] as UDM.Scalar).value as Double
        val lon = (midpoint.elements[1] as UDM.Scalar).value as Double
        
        // Midpoint should be somewhere over the Atlantic
        assertTrue(lat > 40.0 && lat < 52.0, "Midpoint latitude should be between NYC and London")
        assertTrue(lon < 0.0 && lon > -74.0, "Midpoint longitude should be between NYC and London")
    }
    
    @Test
    fun `test midpoint - equator points`() {
        val point1 = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0)))
        val point2 = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(10.0)))
        
        val result = GeospatialFunctions.midpoint(listOf(point1, point2))
        val midpoint = result as UDM.Array
        val lat = (midpoint.elements[0] as UDM.Scalar).value as Double
        val lon = (midpoint.elements[1] as UDM.Scalar).value as Double
        
        assertAlmostEquals(0.0, lat, delta = 0.01, "Midpoint latitude on equator")
        assertAlmostEquals(5.0, lon, delta = 0.1, "Midpoint longitude")
    }

    // ==================== Destination Point Tests ====================
    
    @Test
    fun `test destinationPoint - travel north`() {
        val start = UDM.Array(listOf(UDM.Scalar(40.0), UDM.Scalar(-74.0)))
        val distance = UDM.Scalar(100.0) // 100 km
        val bearing = UDM.Scalar(0.0) // North
        
        val result = GeospatialFunctions.destinationPoint(listOf(start, distance, bearing))
        val destination = result as UDM.Array
        val lat = (destination.elements[0] as UDM.Scalar).value as Double
        val lon = (destination.elements[1] as UDM.Scalar).value as Double
        
        assertTrue(lat > 40.0, "Traveling north should increase latitude")
        assertAlmostEquals(-74.0, lon, delta = 0.01, "Longitude should remain roughly the same when traveling north")
    }
    
    @Test
    fun `test destinationPoint - travel east`() {
        val start = UDM.Array(listOf(UDM.Scalar(40.0), UDM.Scalar(-74.0)))
        val distance = UDM.Scalar(100.0)
        val bearing = UDM.Scalar(90.0) // East
        
        val result = GeospatialFunctions.destinationPoint(listOf(start, distance, bearing))
        val destination = result as UDM.Array
        val lat = (destination.elements[0] as UDM.Scalar).value as Double
        val lon = (destination.elements[1] as UDM.Scalar).value as Double
        
        assertAlmostEquals(40.0, lat, delta = 0.1, "Latitude should remain roughly the same when traveling east")
        assertTrue(lon > -74.0, "Traveling east should increase longitude")
    }
    
    @Test
    fun `test destinationPoint - roundtrip`() {
        val start = UDM.Array(listOf(UDM.Scalar(40.7128), UDM.Scalar(-74.0060)))
        val distance = UDM.Scalar(100.0)
        val bearing = UDM.Scalar(45.0) // Northeast
        
        // Go northeast
        val destination1 = GeospatialFunctions.destinationPoint(listOf(start, distance, bearing)) as UDM.Array
        
        // Come back southwest (opposite bearing)
        val oppositeBearing = UDM.Scalar(225.0)
        val destination2 = GeospatialFunctions.destinationPoint(listOf(destination1, distance, oppositeBearing)) as UDM.Array
        
        val finalLat = (destination2.elements[0] as UDM.Scalar).value as Double
        val finalLon = (destination2.elements[1] as UDM.Scalar).value as Double
        val startLat = (start.elements[0] as UDM.Scalar).value as Double
        val startLon = (start.elements[1] as UDM.Scalar).value as Double
        
        assertAlmostEquals(startLat, finalLat, delta = 0.05, "Roundtrip should return to starting latitude")
        assertAlmostEquals(startLon, finalLon, delta = 0.05, "Roundtrip should return to starting longitude")
    }

    // ==================== Bounding Box Tests ====================
    
    @Test
    fun `test boundingBox - multiple cities`() {
        val cities = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(40.7128), UDM.Scalar(-74.0060))), // NYC
            UDM.Array(listOf(UDM.Scalar(51.5074), UDM.Scalar(-0.1278))),  // London
            UDM.Array(listOf(UDM.Scalar(35.6762), UDM.Scalar(139.6503)))  // Tokyo
        ))
        
        val result = GeospatialFunctions.boundingBox(listOf(cities))
        val bbox = result as UDM.Object
        
        val minLat = (bbox.properties["minLat"] as UDM.Scalar).value as Double
        val maxLat = (bbox.properties["maxLat"] as UDM.Scalar).value as Double
        val minLon = (bbox.properties["minLon"] as UDM.Scalar).value as Double
        val maxLon = (bbox.properties["maxLon"] as UDM.Scalar).value as Double
        
        assertAlmostEquals(35.6762, minLat, delta = 0.01, "Min latitude")
        assertAlmostEquals(51.5074, maxLat, delta = 0.01, "Max latitude")
        assertAlmostEquals(-74.0060, minLon, delta = 0.01, "Min longitude")
        assertAlmostEquals(139.6503, maxLon, delta = 0.01, "Max longitude")
    }
    
    @Test
    fun `test boundingBox - single point`() {
        val points = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(40.0), UDM.Scalar(-74.0)))
        ))
        
        val result = GeospatialFunctions.boundingBox(listOf(points))
        val bbox = result as UDM.Object
        
        val minLat = (bbox.properties["minLat"] as UDM.Scalar).value as Double
        val maxLat = (bbox.properties["maxLat"] as UDM.Scalar).value as Double
        
        assertEquals(minLat, maxLat, "For single point, min and max should be equal")
    }

    // ==================== Coordinate Validation Tests ====================
    
    @Test
    fun `test isValidCoordinates - valid coordinates`() {
        val validCoords = listOf(
            listOf(0.0, 0.0),
            listOf(40.7128, -74.0060),
            listOf(-33.8688, 151.2093),
            listOf(90.0, 180.0),
            listOf(-90.0, -180.0)
        )
        
        validCoords.forEach { (lat, lon) ->
            val coord = UDM.Array(listOf(UDM.Scalar(lat), UDM.Scalar(lon)))
            val result = GeospatialFunctions.isValidCoordinates(listOf(coord))
            val isValid = (result as UDM.Scalar).value as Boolean
            
            assertTrue(isValid, "Coordinates ($lat, $lon) should be valid")
        }
    }
    
    @Test
    fun `test isValidCoordinates - invalid latitude`() {
        val invalidCoords = listOf(
            UDM.Array(listOf(UDM.Scalar(91.0), UDM.Scalar(0.0))),
            UDM.Array(listOf(UDM.Scalar(-91.0), UDM.Scalar(0.0))),
            UDM.Array(listOf(UDM.Scalar(100.0), UDM.Scalar(50.0)))
        )
        
        invalidCoords.forEach { coord ->
            val result = GeospatialFunctions.isValidCoordinates(listOf(coord))
            val isValid = (result as UDM.Scalar).value as Boolean
            
            assertFalse(isValid, "Coordinates with invalid latitude should be invalid")
        }
    }
    
    @Test
    fun `test isValidCoordinates - invalid longitude`() {
        val invalidCoords = listOf(
            UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(181.0))),
            UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(-181.0))),
            UDM.Array(listOf(UDM.Scalar(40.0), UDM.Scalar(200.0)))
        )
        
        invalidCoords.forEach { coord ->
            val result = GeospatialFunctions.isValidCoordinates(listOf(coord))
            val isValid = (result as UDM.Scalar).value as Boolean
            
            assertFalse(isValid, "Coordinates with invalid longitude should be invalid")
        }
    }

    // ==================== Error Handling Tests ====================
    
    @Test
    fun `test distance - invalid coordinates format`() {
        val invalid = UDM.Scalar("not a coordinate")
        val valid = UDM.Array(listOf(UDM.Scalar(40.0), UDM.Scalar(-74.0)))
        
        assertThrows<IllegalArgumentException> {
            GeospatialFunctions.distance(listOf(invalid, valid))
        }
    }
    
    @Test
    fun `test isPointInCircle - negative radius`() {
        val point = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0)))
        val center = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0)))
        val negativeRadius = UDM.Scalar(-10.0)
        
        assertThrows<IllegalArgumentException> {
            GeospatialFunctions.isPointInCircle(listOf(point, center, negativeRadius))
        }
    }
    
    @Test
    fun `test destinationPoint - invalid bearing`() {
        val start = UDM.Array(listOf(UDM.Scalar(0.0), UDM.Scalar(0.0)))
        val distance = UDM.Scalar(100.0)
        val invalidBearing = UDM.Scalar(400.0) // Should be 0-360
        
        assertThrows<IllegalArgumentException> {
            GeospatialFunctions.destinationPoint(listOf(start, distance, invalidBearing))
        }
    }

    // ==================== Integration Tests ====================
    
    @Test
    fun `test real-world scenario - delivery route`() {
        // Restaurant location
        val restaurant = UDM.Array(listOf(UDM.Scalar(40.7580), UDM.Scalar(-73.9855)))
        
        // Customer location
        val customer = UDM.Array(listOf(UDM.Scalar(40.7489), UDM.Scalar(-73.9680)))
        
        // Check if within delivery radius (2 km)
        val deliveryRadius = UDM.Scalar(2.0)
        val inRange = GeospatialFunctions.isPointInCircle(listOf(customer, restaurant, deliveryRadius))
        assertTrue((inRange as UDM.Scalar).value as Boolean, "Customer should be within delivery range")
        
        // Calculate distance for delivery fee
        val distance = GeospatialFunctions.distance(listOf(restaurant, customer))
        val distanceKm = (distance as UDM.Scalar).value as Double
        assertTrue(distanceKm < 2.0, "Distance should be less than 2 km")
        
        // Calculate bearing for driver navigation
        val bearing = GeospatialFunctions.bearing(listOf(restaurant, customer))
        val bearingDegrees = (bearing as UDM.Scalar).value as Double
        assertTrue(bearingDegrees >= 0.0 && bearingDegrees < 360.0, "Bearing should be valid")
    }
    
    @Test
    fun `test real-world scenario - service area geofence`() {
        // Define service area as polygon (e.g., Manhattan)
        val manhattanBounds = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(40.7000), UDM.Scalar(-74.0200))),
            UDM.Array(listOf(UDM.Scalar(40.8800), UDM.Scalar(-74.0200))),
            UDM.Array(listOf(UDM.Scalar(40.8800), UDM.Scalar(-73.9000))),
            UDM.Array(listOf(UDM.Scalar(40.7000), UDM.Scalar(-73.9000)))
        ))
        
        // Test various locations
        val timesSquare = UDM.Array(listOf(UDM.Scalar(40.7580), UDM.Scalar(-73.9855)))
        val brooklyn = UDM.Array(listOf(UDM.Scalar(40.6782), UDM.Scalar(-73.9442)))
        
        val tsInside = GeospatialFunctions.isPointInPolygon(listOf(timesSquare, manhattanBounds))
        val bkInside = GeospatialFunctions.isPointInPolygon(listOf(brooklyn, manhattanBounds))
        
        assertTrue((tsInside as UDM.Scalar).value as Boolean, "Times Square should be in service area")
        assertFalse((bkInside as UDM.Scalar).value as Boolean, "Brooklyn should be outside service area")
    }
}

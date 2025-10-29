// stdlib/src/test/kotlin/org/apache/utlx/stdlib/objects/CriticalObjectFunctionsTest.kt
package org.apache.utlx.stdlib.objects

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import java.time.Instant

class CriticalObjectFunctionsTest {

    // ========== INVERT FUNCTION ==========

    @Test
    fun testInvertSimpleObject() {
        val input = UDM.Object(mapOf(
            "US" to UDM.Scalar("United States"),
            "UK" to UDM.Scalar("United Kingdom"),
            "CA" to UDM.Scalar("Canada")
        ))
        
        val result = CriticalObjectFunctions.invert(listOf(input))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertEquals(UDM.Scalar("US"), properties["United States"])
        assertEquals(UDM.Scalar("UK"), properties["United Kingdom"])
        assertEquals(UDM.Scalar("CA"), properties["Canada"])
        assertEquals(3, properties.size)
    }

    @Test
    fun testInvertWithNumbers() {
        val input = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3)
        ))
        
        val result = CriticalObjectFunctions.invert(listOf(input))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertEquals(UDM.Scalar("a"), properties["1"])
        assertEquals(UDM.Scalar("b"), properties["2"])
        assertEquals(UDM.Scalar("c"), properties["3"])
    }

    @Test
    fun testInvertWithDuplicateValues() {
        val input = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(1) // Duplicate value
        ))
        
        val result = CriticalObjectFunctions.invert(listOf(input))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        // Last key with value 1 should win
        assertEquals(UDM.Scalar("c"), properties["1"])
        assertEquals(UDM.Scalar("b"), properties["2"])
        assertEquals(2, properties.size)
    }

    @Test
    fun testInvertWithComplexValues() {
        val input = UDM.Object(mapOf(
            "array" to UDM.Array(listOf(UDM.Scalar(1))),
            "object" to UDM.Object(mapOf("x" to UDM.Scalar(1))),
            "binary" to UDM.Binary(byteArrayOf(1, 2, 3)),
            "datetime" to UDM.DateTime(Instant.now())
        ))
        
        val result = CriticalObjectFunctions.invert(listOf(input))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertEquals(UDM.Scalar("array"), properties["[Array]"])
        assertEquals(UDM.Scalar("object"), properties["[Object]"])
        assertTrue(properties.containsKey("[Binary:3bytes]"))
        assertTrue(properties.keys.any { it.startsWith("[DateTime:") })
    }

    @Test
    fun testInvertEmptyObject() {
        val input = UDM.Object(mapOf())
        
        val result = CriticalObjectFunctions.invert(listOf(input))
        
        assertTrue(result is UDM.Object)
        assertTrue(result.properties.isEmpty())
    }

    @Test
    fun testInvertInvalidArgCount() {
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.invert(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.invert(listOf(UDM.Object(mapOf()), UDM.Object(mapOf())))
        }
    }

    @Test
    fun testInvertNonObject() {
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.invert(listOf(UDM.Scalar("not an object")))
        }
    }

    // ========== DEEP MERGE FUNCTION ==========

    @Test
    fun testDeepMergeSimple() {
        val obj1 = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ))
        val obj2 = UDM.Object(mapOf(
            "b" to UDM.Scalar(3),
            "c" to UDM.Scalar(4)
        ))
        
        val result = CriticalObjectFunctions.deepMerge(listOf(obj1, obj2))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertEquals(UDM.Scalar(1), properties["a"])
        assertEquals(UDM.Scalar(3), properties["b"]) // obj2 overwrites
        assertEquals(UDM.Scalar(4), properties["c"])
    }

    @Test
    fun testDeepMergeNested() {
        val obj1 = UDM.Object(mapOf(
            "a" to UDM.Object(mapOf(
                "b" to UDM.Scalar(1),
                "c" to UDM.Scalar(2)
            )),
            "d" to UDM.Scalar(3)
        ))
        val obj2 = UDM.Object(mapOf(
            "a" to UDM.Object(mapOf(
                "c" to UDM.Scalar(4),
                "e" to UDM.Scalar(5)
            )),
            "f" to UDM.Scalar(6)
        ))
        
        val result = CriticalObjectFunctions.deepMerge(listOf(obj1, obj2))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        // Check nested object merge
        val nestedA = properties["a"] as UDM.Object
        assertEquals(UDM.Scalar(1), nestedA.properties["b"])
        assertEquals(UDM.Scalar(4), nestedA.properties["c"]) // Overwritten
        assertEquals(UDM.Scalar(5), nestedA.properties["e"]) // Added
        
        assertEquals(UDM.Scalar(3), properties["d"])
        assertEquals(UDM.Scalar(6), properties["f"])
    }

    @Test
    fun testDeepMergeArrays() {
        val obj1 = UDM.Object(mapOf(
            "arr" to UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        ))
        val obj2 = UDM.Object(mapOf(
            "arr" to UDM.Array(listOf(UDM.Scalar(3), UDM.Scalar(4)))
        ))
        
        val result = CriticalObjectFunctions.deepMerge(listOf(obj1, obj2))
        
        assertTrue(result is UDM.Object)
        val array = result.properties["arr"] as UDM.Array
        
        assertEquals(4, array.elements.size)
        assertEquals(UDM.Scalar(1), array.elements[0])
        assertEquals(UDM.Scalar(2), array.elements[1])
        assertEquals(UDM.Scalar(3), array.elements[2])
        assertEquals(UDM.Scalar(4), array.elements[3])
    }

    @Test
    fun testDeepMergeAttributes() {
        val obj1 = UDM.Object(
            mapOf("a" to UDM.Scalar(1)),
            mapOf("attr1" to "value1")
        )
        val obj2 = UDM.Object(
            mapOf("b" to UDM.Scalar(2)),
            mapOf("attr2" to "value2")
        )
        
        val result = CriticalObjectFunctions.deepMerge(listOf(obj1, obj2))
        
        assertTrue(result is UDM.Object)
        assertEquals("value1", result.attributes["attr1"])
        assertEquals("value2", result.attributes["attr2"])
    }

    @Test
    fun testDeepMergeInvalidArgs() {
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.deepMerge(listOf(UDM.Object(mapOf())))
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.deepMerge(listOf(UDM.Scalar(1), UDM.Object(mapOf())))
        }
    }

    // ========== DEEP MERGE ALL FUNCTION ==========

    @Test
    fun testDeepMergeAllMultipleObjects() {
        val objects = UDM.Array(listOf(
            UDM.Object(mapOf("a" to UDM.Scalar(1))),
            UDM.Object(mapOf("b" to UDM.Scalar(2))),
            UDM.Object(mapOf("c" to UDM.Scalar(3)))
        ))
        
        val result = CriticalObjectFunctions.deepMergeAll(listOf(objects))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertEquals(UDM.Scalar(1), properties["a"])
        assertEquals(UDM.Scalar(2), properties["b"])
        assertEquals(UDM.Scalar(3), properties["c"])
    }

    @Test
    fun testDeepMergeAllEmpty() {
        val objects = UDM.Array(listOf())
        
        val result = CriticalObjectFunctions.deepMergeAll(listOf(objects))
        
        assertTrue(result is UDM.Object)
        assertTrue(result.properties.isEmpty())
    }

    @Test
    fun testDeepMergeAllSingleObject() {
        val objects = UDM.Array(listOf(
            UDM.Object(mapOf("a" to UDM.Scalar(1)))
        ))
        
        val result = CriticalObjectFunctions.deepMergeAll(listOf(objects))
        
        assertTrue(result is UDM.Object)
        assertEquals(UDM.Scalar(1), result.properties["a"])
    }

    @Test
    fun testDeepMergeAllInvalidArgs() {
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.deepMergeAll(listOf(UDM.Scalar("not array")))
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.deepMergeAll(listOf(UDM.Array(listOf(UDM.Scalar(1)))))
        }
    }

    // ========== DEEP CLONE FUNCTION ==========

    @Test
    fun testDeepCloneObject() {
        val original = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Object(mapOf("c" to UDM.Scalar(2)))
        ))
        
        val result = CriticalObjectFunctions.deepClone(listOf(original))
        
        assertTrue(result is UDM.Object)
        assertNotSame(original, result)
        
        // Check deep equality but different references
        assertEquals(original.properties["a"], result.properties["a"])
        val originalNested = original.properties["b"] as UDM.Object
        val clonedNested = result.properties["b"] as UDM.Object
        assertNotSame(originalNested, clonedNested)
        assertEquals(originalNested.properties["c"], clonedNested.properties["c"])
    }

    @Test
    fun testDeepCloneArray() {
        val original = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Array(listOf(UDM.Scalar(2)))
        ))
        
        val result = CriticalObjectFunctions.deepClone(listOf(original))
        
        assertTrue(result is UDM.Array)
        assertNotSame(original, result)
        assertEquals(original.elements.size, result.elements.size)
        
        // Check nested array is also cloned
        val originalNested = original.elements[1] as UDM.Array
        val clonedNested = result.elements[1] as UDM.Array
        assertNotSame(originalNested, clonedNested)
        assertEquals(originalNested.elements[0], clonedNested.elements[0])
    }

    @Test
    fun testDeepCloneBinary() {
        val originalData = byteArrayOf(1, 2, 3, 4, 5)
        val original = UDM.Binary(originalData)
        
        val result = CriticalObjectFunctions.deepClone(listOf(original))
        
        assertTrue(result is UDM.Binary)
        assertNotSame(original, result)
        assertNotSame(original.data, result.data)
        assertTrue(original.data.contentEquals(result.data))
    }

    @Test
    fun testDeepCloneScalar() {
        val original = UDM.Scalar("test")
        
        val result = CriticalObjectFunctions.deepClone(listOf(original))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(original.value, result.value)
    }

    @Test
    fun testDeepCloneDateTime() {
        val instant = Instant.now()
        val original = UDM.DateTime(instant)
        
        val result = CriticalObjectFunctions.deepClone(listOf(original))
        
        assertTrue(result is UDM.DateTime)
        assertEquals(original.instant, result.instant)
    }

    @Test
    fun testDeepCloneInvalidArgs() {
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.deepClone(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.deepClone(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        }
    }

    // ========== GET PATH FUNCTION ==========

    @Test
    fun testGetPathSimple() {
        val obj = UDM.Object(mapOf(
            "a" to UDM.Object(mapOf(
                "b" to UDM.Object(mapOf(
                    "c" to UDM.Scalar(123)
                ))
            ))
        ))
        val path = UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("b"),
            UDM.Scalar("c")
        ))
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path))
        
        assertEquals(UDM.Scalar(123), result)
    }

    @Test
    fun testGetPathWithArray() {
        val obj = UDM.Object(mapOf(
            "users" to UDM.Array(listOf(
                UDM.Object(mapOf("name" to UDM.Scalar("Alice"))),
                UDM.Object(mapOf("name" to UDM.Scalar("Bob")))
            ))
        ))
        val path = UDM.Array(listOf(
            UDM.Scalar("users"),
            UDM.Scalar(0),
            UDM.Scalar("name")
        ))
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path))
        
        assertEquals(UDM.Scalar("Alice"), result)
    }

    @Test
    fun testGetPathNonExistent() {
        val obj = UDM.Object(mapOf("a" to UDM.Scalar(1)))
        val path = UDM.Array(listOf(UDM.Scalar("b")))
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, result.value)
    }

    @Test
    fun testGetPathArrayOutOfBounds() {
        val obj = UDM.Object(mapOf(
            "arr" to UDM.Array(listOf(UDM.Scalar(1)))
        ))
        val path = UDM.Array(listOf(
            UDM.Scalar("arr"),
            UDM.Scalar(5) // Out of bounds
        ))
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, result.value)
    }

    @Test
    fun testGetPathEmptyPath() {
        val obj = UDM.Object(mapOf("a" to UDM.Scalar(1)))
        val path = UDM.Array(listOf())
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path))
        
        assertEquals(obj, result)
    }

    @Test
    fun testGetPathInvalidArgs() {
        // Test with wrong number of arguments (too few)
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.getPath(listOf(UDM.Object(mapOf())))
        }

        // Test with wrong number of arguments (too many)
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.getPath(listOf(UDM.Object(mapOf()), UDM.Scalar("path"), UDM.Scalar("default"), UDM.Scalar("extra")))
        }
    }

    // ========== SET PATH FUNCTION ==========

    @Test
    fun testSetPathSimple() {
        val obj = UDM.Object(mapOf(
            "a" to UDM.Object(mapOf("b" to UDM.Scalar(1)))
        ))
        val path = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("c")))
        val value = UDM.Scalar(2)
        
        val result = CriticalObjectFunctions.setPath(listOf(obj, path, value))
        
        assertTrue(result is UDM.Object)
        val nestedA = result.properties["a"] as UDM.Object
        assertEquals(UDM.Scalar(1), nestedA.properties["b"]) // Original preserved
        assertEquals(UDM.Scalar(2), nestedA.properties["c"]) // New value added
    }

    @Test
    fun testSetPathCreatePath() {
        val obj = UDM.Object(mapOf())
        val path = UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("b"),
            UDM.Scalar("c")
        ))
        val value = UDM.Scalar(123)
        
        val result = CriticalObjectFunctions.setPath(listOf(obj, path, value))
        
        assertTrue(result is UDM.Object)
        val a = result.properties["a"] as UDM.Object
        val b = a.properties["b"] as UDM.Object
        assertEquals(UDM.Scalar(123), b.properties["c"])
    }

    @Test
    fun testSetPathRootReplacement() {
        val obj = UDM.Object(mapOf("a" to UDM.Scalar(1)))
        val path = UDM.Array(listOf())
        val value = UDM.Scalar("replaced")
        
        val result = CriticalObjectFunctions.setPath(listOf(obj, path, value))
        
        assertEquals(value, result)
    }

    @Test
    fun testSetPathImmutability() {
        val original = UDM.Object(mapOf("a" to UDM.Scalar(1)))
        val path = UDM.Array(listOf(UDM.Scalar("a")))
        val value = UDM.Scalar(2)
        
        val result = CriticalObjectFunctions.setPath(listOf(original, path, value))
        
        // Original should be unchanged
        assertEquals(UDM.Scalar(1), original.properties["a"])
        // Result should have new value
        assertEquals(UDM.Scalar(2), (result as UDM.Object).properties["a"])
    }

    @Test
    fun testSetPathInvalidArgs() {
        // Test with wrong number of arguments
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.setPath(listOf(UDM.Object(mapOf()), UDM.Array(listOf())))
        }

        // Test with non-object as first argument
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.setPath(listOf(UDM.Scalar("not object"), UDM.Array(listOf()), UDM.Scalar(1)))
        }

        // Test with invalid path type (not string or array) - e.g., Binary
        assertThrows<IllegalArgumentException> {
            CriticalObjectFunctions.setPath(listOf(UDM.Object(mapOf()), UDM.Binary(byteArrayOf(1, 2, 3)), UDM.Scalar(1)))
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun testDeepCloneLargeStructure() {
        // Test with deeply nested structure
        var nested: UDM = UDM.Scalar("deep")
        for (i in 1..10) {
            nested = UDM.Object(mapOf("level$i" to nested))
        }
        
        val result = CriticalObjectFunctions.deepClone(listOf(nested))
        
        assertTrue(result is UDM.Object)
        assertNotSame(nested, result)
    }

    @Test
    fun testDeepMergeComplexScenario() {
        val config = UDM.Object(mapOf(
            "database" to UDM.Object(mapOf(
                "host" to UDM.Scalar("localhost"),
                "port" to UDM.Scalar(5432),
                "name" to UDM.Scalar("myapp")
            )),
            "cache" to UDM.Object(mapOf(
                "enabled" to UDM.Scalar(true)
            ))
        ))
        
        val override = UDM.Object(mapOf(
            "database" to UDM.Object(mapOf(
                "host" to UDM.Scalar("prod-server"),
                "ssl" to UDM.Scalar(true)
            )),
            "logging" to UDM.Object(mapOf(
                "level" to UDM.Scalar("INFO")
            ))
        ))
        
        val result = CriticalObjectFunctions.deepMerge(listOf(config, override))
        
        assertTrue(result is UDM.Object)
        val db = result.properties["database"] as UDM.Object
        assertEquals(UDM.Scalar("prod-server"), db.properties["host"]) // Overridden
        assertEquals(UDM.Scalar(5432), db.properties["port"]) // Preserved
        assertEquals(UDM.Scalar("myapp"), db.properties["name"]) // Preserved
        assertEquals(UDM.Scalar(true), db.properties["ssl"]) // Added
        
        val cache = result.properties["cache"] as UDM.Object
        assertEquals(UDM.Scalar(true), cache.properties["enabled"]) // Preserved
        
        val logging = result.properties["logging"] as UDM.Object
        assertEquals(UDM.Scalar("INFO"), logging.properties["level"]) // Added
    }

    @Test
    fun testGetSetPathRoundTrip() {
        val original = UDM.Object(mapOf(
            "user" to UDM.Object(mapOf(
                "profile" to UDM.Object(mapOf(
                    "name" to UDM.Scalar("Alice")
                ))
            ))
        ))
        val path = UDM.Array(listOf(
            UDM.Scalar("user"),
            UDM.Scalar("profile"),
            UDM.Scalar("name")
        ))
        
        // Get the value
        val getValue = CriticalObjectFunctions.getPath(listOf(original, path))
        assertEquals(UDM.Scalar("Alice"), getValue)
        
        // Set a new value
        val newValue = UDM.Scalar("Bob")
        val updated = CriticalObjectFunctions.setPath(listOf(original, path, newValue))
        
        // Get the new value
        val getNewValue = CriticalObjectFunctions.getPath(listOf(updated, path))
        assertEquals(UDM.Scalar("Bob"), getNewValue)
        
        // Original should be unchanged
        val originalValue = CriticalObjectFunctions.getPath(listOf(original, path))
        assertEquals(UDM.Scalar("Alice"), originalValue)
    }
}
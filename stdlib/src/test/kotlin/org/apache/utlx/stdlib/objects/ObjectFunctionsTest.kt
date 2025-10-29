// stdlib/src/test/kotlin/org/apache/utlx/stdlib/objects/ObjectFunctionsTest.kt
package org.apache.utlx.stdlib.objects

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for Object functions.
 * 
 * Tests cover:
 * - Basic object operations (keys, values, entries)
 * - Object manipulation (merge, pick, omit)
 * - Deep operations (deepMerge, deepClone)
 * - Path-based access (getPath, setPath)
 * - Object transformations (invert, mapEntries, etc.)
 * - Enhanced object functions
 */
class ObjectFunctionsTest {

    // ==================== Basic Object Functions Tests ====================
    
    @Test
    fun `test keys - simple object`() {
        val obj = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30),
            "city" to UDM.Scalar("New York")
        ))
        
        val result = ObjectFunctions.keys(listOf(obj))
        val keys = result as UDM.Array
        
        assertEquals(3, keys.elements.size)
        val keyStrings = keys.elements.map { (it as UDM.Scalar).value as String }
        assertTrue(keyStrings.contains("name"))
        assertTrue(keyStrings.contains("age"))
        assertTrue(keyStrings.contains("city"))
    }
    
    @Test
    fun `test keys - empty object`() {
        val obj = UDM.Object(mutableMapOf())
        
        val result = ObjectFunctions.keys(listOf(obj))
        val keys = result as UDM.Array
        
        assertEquals(0, keys.elements.size, "Empty object should have no keys")
    }
    
    @Test
    fun `test values - simple object`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3)
        ))
        
        val result = ObjectFunctions.values(listOf(obj))
        val values = result as UDM.Array
        
        assertEquals(3, values.elements.size)
        val valueInts = values.elements.map { (it as UDM.Scalar).value as Int }
        assertTrue(valueInts.contains(1))
        assertTrue(valueInts.contains(2))
        assertTrue(valueInts.contains(3))
    }
    
    @Test
    fun `test entries - simple object`() {
        val obj = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ))
        
        val result = ObjectFunctions.entries(listOf(obj))
        val entries = result as UDM.Array
        
        assertEquals(2, entries.elements.size)
        
        // Each entry should be an array [key, value]
        entries.elements.forEach { entry ->
            val pair = entry as UDM.Array
            assertEquals(2, pair.elements.size, "Each entry should be [key, value]")
        }
    }
    
    @Test
    fun `test entries - verify structure`() {
        val obj = UDM.Object(mutableMapOf(
            "x" to UDM.Scalar(10)
        ))
        
        val result = ObjectFunctions.entries(listOf(obj))
        val entries = result as UDM.Array
        
        val entry = entries.elements[0] as UDM.Array
        val key = (entry.elements[0] as UDM.Scalar).value as String
        val value = (entry.elements[1] as UDM.Scalar).value as Int
        
        assertEquals("x", key)
        assertEquals(10, value)
    }

    // ==================== Merge Tests ====================
    
    @Test
    fun `test merge - two objects`() {
        val obj1 = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ))
        val obj2 = UDM.Object(mutableMapOf(
            "c" to UDM.Scalar(3),
            "d" to UDM.Scalar(4)
        ))
        
        val result = ObjectFunctions.merge(listOf(obj1, obj2))
        val merged = result as UDM.Object
        
        assertEquals(4, merged.properties.size)
        assertEquals(1, (merged.properties["a"] as UDM.Scalar).value)
        assertEquals(3, (merged.properties["c"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test merge - overlapping keys (second wins)`() {
        val obj1 = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("Alice"),
            "age" to UDM.Scalar(25)
        ))
        val obj2 = UDM.Object(mutableMapOf(
            "age" to UDM.Scalar(30),
            "city" to UDM.Scalar("NYC")
        ))
        
        val result = ObjectFunctions.merge(listOf(obj1, obj2))
        val merged = result as UDM.Object
        
        assertEquals(3, merged.properties.size)
        assertEquals("Alice", (merged.properties["name"] as UDM.Scalar).value)
        assertEquals(30, (merged.properties["age"] as UDM.Scalar).value, "Second object should win")
        assertEquals("NYC", (merged.properties["city"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test merge - multiple objects`() {
        val obj1 = UDM.Object(mutableMapOf("a" to UDM.Scalar(1)))
        val obj2 = UDM.Object(mutableMapOf("b" to UDM.Scalar(2)))
        val obj3 = UDM.Object(mutableMapOf("c" to UDM.Scalar(3)))
        
        val result = ObjectFunctions.merge(listOf(obj1, obj2, obj3))
        val merged = result as UDM.Object
        
        assertEquals(3, merged.properties.size)
    }

    // ==================== Pick/Omit Tests ====================
    
    @Test
    fun `test pick - select specific keys`() {
        val obj = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30),
            "city" to UDM.Scalar("NYC"),
            "country" to UDM.Scalar("USA")
        ))
        val keys = UDM.Array(listOf(
            UDM.Scalar("name"),
            UDM.Scalar("city")
        ))
        
        val result = ObjectFunctions.pick(listOf(obj, keys))
        val picked = result as UDM.Object
        
        assertEquals(2, picked.properties.size)
        assertTrue(picked.properties.containsKey("name"))
        assertTrue(picked.properties.containsKey("city"))
        assertFalse(picked.properties.containsKey("age"))
        assertFalse(picked.properties.containsKey("country"))
    }
    
    @Test
    fun `test pick - non-existent keys`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1)
        ))
        val keys = UDM.Array(listOf(
            UDM.Scalar("b"),
            UDM.Scalar("c")
        ))
        
        val result = ObjectFunctions.pick(listOf(obj, keys))
        val picked = result as UDM.Object
        
        assertEquals(0, picked.properties.size, "Should return empty object for non-existent keys")
    }
    
    @Test
    fun `test omit - exclude specific keys`() {
        val obj = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30),
            "password" to UDM.Scalar("secret123"),
            "email" to UDM.Scalar("john@example.com")
        ))
        val keys = UDM.Array(listOf(
            UDM.Scalar("password")
        ))
        
        val result = ObjectFunctions.omit(listOf(obj, keys))
        val omitted = result as UDM.Object
        
        assertEquals(3, omitted.properties.size)
        assertTrue(omitted.properties.containsKey("name"))
        assertTrue(omitted.properties.containsKey("age"))
        assertTrue(omitted.properties.containsKey("email"))
        assertFalse(omitted.properties.containsKey("password"))
    }
    
    @Test
    fun `test omit - multiple keys`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3),
            "d" to UDM.Scalar(4)
        ))
        val keys = UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("c")
        ))
        
        val result = ObjectFunctions.omit(listOf(obj, keys))
        val omitted = result as UDM.Object
        
        assertEquals(2, omitted.properties.size)
        assertTrue(omitted.properties.containsKey("b"))
        assertTrue(omitted.properties.containsKey("d"))
    }

    // ==================== Deep Operations Tests ====================
    
    @Test
    fun `test deepMerge - nested objects`() {
        val obj1 = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice"),
                "age" to UDM.Scalar(25)
            ))
        ))
        val obj2 = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "age" to UDM.Scalar(30),
                "city" to UDM.Scalar("NYC")
            ))
        ))
        
        val result = CriticalObjectFunctions.deepMerge(listOf(obj1, obj2))
        val merged = result as UDM.Object
        
        val user = merged.properties["user"] as UDM.Object
        assertEquals("Alice", (user.properties["name"] as UDM.Scalar).value)
        assertEquals(30, (user.properties["age"] as UDM.Scalar).value, "Should merge nested values")
        assertEquals("NYC", (user.properties["city"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test deepMergeAll - multiple nested objects`() {
        val objs = UDM.Array(listOf(
            UDM.Object(mutableMapOf("a" to UDM.Scalar(1))),
            UDM.Object(mutableMapOf("b" to UDM.Scalar(2))),
            UDM.Object(mutableMapOf("c" to UDM.Scalar(3)))
        ))
        
        val result = CriticalObjectFunctions.deepMergeAll(listOf(objs))
        val merged = result as UDM.Object
        
        assertEquals(3, merged.properties.size)
    }
    
    @Test
    fun `test deepClone - simple object`() {
        val original = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ))
        
        val result = CriticalObjectFunctions.deepClone(listOf(original))
        val cloned = result as UDM.Object
        
        // Verify values are same
        assertEquals((original.properties["name"] as UDM.Scalar).value,
                    (cloned.properties["name"] as UDM.Scalar).value)
        
        // Verify it's a different object
        assertNotSame(original, cloned, "Should be a different object instance")
    }
    
    @Test
    fun `test deepClone - nested object`() {
        val original = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice"),
                "address" to UDM.Object(mutableMapOf(
                    "city" to UDM.Scalar("NYC")
                ))
            ))
        ))
        
        val result = CriticalObjectFunctions.deepClone(listOf(original))
        val cloned = result as UDM.Object
        
        // Verify nested structure
        val clonedUser = cloned.properties["user"] as UDM.Object
        val clonedAddress = clonedUser.properties["address"] as UDM.Object
        assertEquals("NYC", (clonedAddress.properties["city"] as UDM.Scalar).value)
        
        // Verify independence (not same references)
        assertNotSame(original.properties["user"], cloned.properties["user"])
    }
    
    @Test
    fun `test invert - swap keys and values`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar("1"),
            "b" to UDM.Scalar("2"),
            "c" to UDM.Scalar("3")
        ))
        
        val result = CriticalObjectFunctions.invert(listOf(obj))
        val inverted = result as UDM.Object
        
        assertEquals("a", (inverted.properties["1"] as UDM.Scalar).value)
        assertEquals("b", (inverted.properties["2"] as UDM.Scalar).value)
        assertEquals("c", (inverted.properties["3"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test invert - duplicate values (last wins)`() {
        val obj = UDM.Object(mutableMapOf(
            "key1" to UDM.Scalar("value"),
            "key2" to UDM.Scalar("value")
        ))
        
        val result = CriticalObjectFunctions.invert(listOf(obj))
        val inverted = result as UDM.Object
        
        // Only one "value" key should exist (last wins)
        assertEquals(1, inverted.properties.size)
        assertTrue(inverted.properties.containsKey("value"))
    }

    // ==================== Path-Based Access Tests ====================
    
    @Test
    fun `test getPath - simple path`() {
        val obj = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice")
            ))
        ))
        val path = UDM.Scalar("user.name")
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path))
        val value = (result as UDM.Scalar).value as String
        
        assertEquals("Alice", value)
    }
    
    @Test
    fun `test getPath - nested path`() {
        val obj = UDM.Object(mutableMapOf(
            "company" to UDM.Object(mutableMapOf(
                "department" to UDM.Object(mutableMapOf(
                    "employee" to UDM.Object(mutableMapOf(
                        "name" to UDM.Scalar("Bob")
                    ))
                ))
            ))
        ))
        val path = UDM.Scalar("company.department.employee.name")
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path))
        val value = (result as UDM.Scalar).value as String
        
        assertEquals("Bob", value)
    }
    
    @Test
    fun `test getPath - non-existent path`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1)
        ))
        val path = UDM.Scalar("b.c.d")
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path))
        
        // Should return null or some default value for non-existent path
        assertTrue(result is UDM.Scalar && (result.value == null || result.value == ""))
    }
    
    @Test
    fun `test getPath - with default value`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1)
        ))
        val path = UDM.Scalar("b.c.d")
        val defaultValue = UDM.Scalar("default")
        
        val result = CriticalObjectFunctions.getPath(listOf(obj, path, defaultValue))
        val value = (result as UDM.Scalar).value as String
        
        assertEquals("default", value, "Should return default for non-existent path")
    }
    
    @Test
    fun `test setPath - simple path`() {
        val obj = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf())
        ))
        val path = UDM.Scalar("user.name")
        val value = UDM.Scalar("Charlie")
        
        val result = CriticalObjectFunctions.setPath(listOf(obj, path, value))
        val modified = result as UDM.Object
        
        val user = modified.properties["user"] as UDM.Object
        assertEquals("Charlie", (user.properties["name"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test setPath - create nested path`() {
        val obj = UDM.Object(mutableMapOf())
        val path = UDM.Scalar("a.b.c")
        val value = UDM.Scalar("deep value")
        
        val result = CriticalObjectFunctions.setPath(listOf(obj, path, value))
        val modified = result as UDM.Object
        
        // Verify nested structure was created
        val a = modified.properties["a"] as UDM.Object
        val b = a.properties["b"] as UDM.Object
        assertEquals("deep value", (b.properties["c"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test setPath - overwrite existing value`() {
        val obj = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice")
            ))
        ))
        val path = UDM.Scalar("user.name")
        val value = UDM.Scalar("Bob")
        
        val result = CriticalObjectFunctions.setPath(listOf(obj, path, value))
        val modified = result as UDM.Object
        
        val user = modified.properties["user"] as UDM.Object
        assertEquals("Bob", (user.properties["name"] as UDM.Scalar).value)
    }

    // ==================== Enhanced Object Functions Tests ====================
    
    @Test
    fun `test divideBy - partition by property`() {
        val items = UDM.Array(listOf(
            UDM.Object(mutableMapOf("type" to UDM.Scalar("A"), "value" to UDM.Scalar(1))),
            UDM.Object(mutableMapOf("type" to UDM.Scalar("B"), "value" to UDM.Scalar(2))),
            UDM.Object(mutableMapOf("type" to UDM.Scalar("A"), "value" to UDM.Scalar(3)))
        ))
        val key = UDM.Scalar("type")
        
        val result = EnhancedObjectFunctions.divideBy(listOf(items, key))
        val divided = result as UDM.Object
        
        assertTrue(divided.properties.containsKey("A"))
        assertTrue(divided.properties.containsKey("B"))
        
        val groupA = divided.properties["A"] as UDM.Array
        assertEquals(2, groupA.elements.size)
    }
    
    @Test
    fun `test someEntry - at least one entry matches`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3)
        ))
        // Predicate: value > 2
        val predicate = UDM.Lambda { args ->
            val value = (args[1] as UDM.Scalar).value as Number
            UDM.Scalar(value.toDouble() > 2.0)
        }

        val result = EnhancedObjectFunctions.someEntry(listOf(obj, predicate))
        assertTrue(result is UDM.Scalar)
        assertEquals(true, (result as UDM.Scalar).value) // c=3 is > 2
    }
    
    @Test
    fun `test everyEntry - all entries match`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(5),
            "b" to UDM.Scalar(10),
            "c" to UDM.Scalar(15)
        ))
        // Predicate: value > 0
        val predicate = UDM.Lambda { args ->
            val value = (args[1] as UDM.Scalar).value as Number
            UDM.Scalar(value.toDouble() > 0.0)
        }

        val result = EnhancedObjectFunctions.everyEntry(listOf(obj, predicate))
        assertTrue(result is UDM.Scalar)
        assertEquals(true, (result as UDM.Scalar).value) // All values > 0
    }
    
    @Test
    fun `test mapValues - transform all values`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3)
        ))
        // Function: multiply by 2
        val mapper = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            UDM.Scalar(value.toDouble() * 2)
        }

        val result = EnhancedObjectFunctions.mapValues(listOf(obj, mapper))
        val mapped = result as UDM.Object

        assertEquals(3, mapped.properties.size)
        assertTrue(mapped.properties.containsKey("a"))
        assertEquals(2, (mapped.properties["a"] as UDM.Scalar).value) // 1 * 2 = 2
        assertEquals(4, (mapped.properties["b"] as UDM.Scalar).value) // 2 * 2 = 4
        assertEquals(6, (mapped.properties["c"] as UDM.Scalar).value) // 3 * 2 = 6
    }
    
    @Test
    fun `test mapKeys - transform all keys`() {
        val obj = UDM.Object(mutableMapOf(
            "first_name" to UDM.Scalar("John"),
            "last_name" to UDM.Scalar("Doe")
        ))
        // Function: convert snake_case to camelCase
        val mapper = UDM.Lambda { args ->
            val key = (args[0] as UDM.Scalar).value.toString()
            // Convert snake_case to camelCase
            val camelCase = key.split("_").mapIndexed { index, part ->
                if (index == 0) part else part.replaceFirstChar { it.uppercase() }
            }.joinToString("")
            UDM.Scalar(camelCase)
        }

        val result = EnhancedObjectFunctions.mapKeys(listOf(obj, mapper))
        val mapped = result as UDM.Object

        assertEquals(2, mapped.properties.size)
        assertTrue(mapped.properties.containsKey("firstName"))
        assertTrue(mapped.properties.containsKey("lastName"))
        assertEquals("John", (mapped.properties["firstName"] as UDM.Scalar).value)
        assertEquals("Doe", (mapped.properties["lastName"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test countEntries - count matching entries`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3),
            "d" to UDM.Scalar(4)
        ))
        // Predicate: value > 2
        val predicate = UDM.Lambda { args ->
            val value = (args[1] as UDM.Scalar).value as Number
            UDM.Scalar(value.toDouble() > 2.0)
        }

        val result = EnhancedObjectFunctions.countEntries(listOf(obj, predicate))
        val count = (result as UDM.Scalar).value as Int

        // Values 3 and 4 are > 2, so count should be 2
        assertEquals(2, count)
    }

    // ==================== Real-World Scenarios ====================
    
    @Test
    fun `test real-world - API response transformation`() {
        val apiResponse = UDM.Object(mutableMapOf(
            "user_id" to UDM.Scalar(12345),
            "user_name" to UDM.Scalar("johndoe"),
            "email_address" to UDM.Scalar("john@example.com"),
            "created_at" to UDM.Scalar("2025-01-01"),
            "internal_token" to UDM.Scalar("secret123")
        ))
        
        // Remove sensitive data
        val publicData = ObjectFunctions.omit(listOf(
            apiResponse,
            UDM.Array(listOf(UDM.Scalar("internal_token")))
        ))
        
        val cleaned = publicData as UDM.Object
        assertFalse(cleaned.properties.containsKey("internal_token"))
        assertTrue(cleaned.properties.containsKey("email_address"))
    }
    
    @Test
    fun `test real-world - configuration merge`() {
        val defaultConfig = UDM.Object(mutableMapOf(
            "timeout" to UDM.Scalar(30),
            "retries" to UDM.Scalar(3),
            "debug" to UDM.Scalar(false)
        ))
        val userConfig = UDM.Object(mutableMapOf(
            "timeout" to UDM.Scalar(60),
            "debug" to UDM.Scalar(true)
        ))
        
        val finalConfig = ObjectFunctions.merge(listOf(defaultConfig, userConfig))
        val config = finalConfig as UDM.Object
        
        assertEquals(60, (config.properties["timeout"] as UDM.Scalar).value)
        assertEquals(3, (config.properties["retries"] as UDM.Scalar).value)
        assertEquals(true, (config.properties["debug"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test real-world - form data extraction`() {
        val formData = UDM.Object(mutableMapOf(
            "firstName" to UDM.Scalar("John"),
            "lastName" to UDM.Scalar("Doe"),
            "email" to UDM.Scalar("john@example.com"),
            "newsletter" to UDM.Scalar(true),
            "csrf_token" to UDM.Scalar("abc123"),
            "submit_button" to UDM.Scalar("Submit")
        ))
        
        // Extract only user data
        val userData = ObjectFunctions.pick(listOf(
            formData,
            UDM.Array(listOf(
                UDM.Scalar("firstName"),
                UDM.Scalar("lastName"),
                UDM.Scalar("email"),
                UDM.Scalar("newsletter")
            ))
        ))
        
        val user = userData as UDM.Object
        assertEquals(4, user.properties.size)
        assertFalse(user.properties.containsKey("csrf_token"))
        assertFalse(user.properties.containsKey("submit_button"))
    }
    
    @Test
    fun `test real-world - nested configuration access`() {
        val config = UDM.Object(mutableMapOf(
            "database" to UDM.Object(mutableMapOf(
                "connection" to UDM.Object(mutableMapOf(
                    "host" to UDM.Scalar("localhost"),
                    "port" to UDM.Scalar(5432)
                ))
            ))
        ))
        
        val host = CriticalObjectFunctions.getPath(
            listOf(config, UDM.Scalar("database.connection.host"))
        )
        assertEquals("localhost", (host as UDM.Scalar).value)
        
        val port = CriticalObjectFunctions.getPath(
            listOf(config, UDM.Scalar("database.connection.port"))
        )
        assertEquals(5432, (port as UDM.Scalar).value)
    }
    
    @Test
    fun `test real-world - user profile update`() {
        val existingProfile = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice"),
                "email" to UDM.Scalar("alice@example.com"),
                "settings" to UDM.Object(mutableMapOf(
                    "theme" to UDM.Scalar("light"),
                    "notifications" to UDM.Scalar(true)
                ))
            ))
        ))
        
        // Update nested value
        val updated = CriticalObjectFunctions.setPath(
            listOf(existingProfile, UDM.Scalar("user.settings.theme"), UDM.Scalar("dark"))
        )
        
        val profile = updated as UDM.Object
        val user = profile.properties["user"] as UDM.Object
        val settings = user.properties["settings"] as UDM.Object
        assertEquals("dark", (settings.properties["theme"] as UDM.Scalar).value)
        assertEquals(true, (settings.properties["notifications"] as UDM.Scalar).value)
    }

    // ==================== Edge Cases ====================
    
    @Test
    fun `test edge case - empty object operations`() {
        val empty = UDM.Object(mutableMapOf())
        
        // Keys of empty object
        val keys = ObjectFunctions.keys(listOf(empty)) as UDM.Array
        assertEquals(0, keys.elements.size)
        
        // Values of empty object
        val values = ObjectFunctions.values(listOf(empty)) as UDM.Array
        assertEquals(0, values.elements.size)
        
        // Entries of empty object
        val entries = ObjectFunctions.entries(listOf(empty)) as UDM.Array
        assertEquals(0, entries.elements.size)
    }
    
    @Test
    fun `test edge case - merge with empty object`() {
        val obj = UDM.Object(mutableMapOf("a" to UDM.Scalar(1)))
        val empty = UDM.Object(mutableMapOf())
        
        val result1 = ObjectFunctions.merge(listOf(obj, empty))
        assertEquals(1, (result1 as UDM.Object).properties.size)
        
        val result2 = ObjectFunctions.merge(listOf(empty, obj))
        assertEquals(1, (result2 as UDM.Object).properties.size)
    }
    
    @Test
    fun `test edge case - pick all keys`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ))
        val allKeys = UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("b")
        ))
        
        val result = ObjectFunctions.pick(listOf(obj, allKeys))
        val picked = result as UDM.Object
        
        assertEquals(2, picked.properties.size)
    }
    
    @Test
    fun `test edge case - omit all keys`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ))
        val allKeys = UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("b")
        ))
        
        val result = ObjectFunctions.omit(listOf(obj, allKeys))
        val omitted = result as UDM.Object
        
        assertEquals(0, omitted.properties.size, "Omitting all keys should result in empty object")
    }
    
    @Test
    fun `test edge case - deeply nested path`() {
        val obj = UDM.Object(mutableMapOf())
        val deepPath = UDM.Scalar("a.b.c.d.e.f.g.h.i.j")
        val value = UDM.Scalar("deep")
        
        val result = CriticalObjectFunctions.setPath(listOf(obj, deepPath, value))
        val modified = result as UDM.Object
        
        // Verify deep structure was created
        assertNotNull(modified.properties["a"])
    }
    
    @Test
    fun `test edge case - null and undefined handling`() {
        val obj = UDM.Object(mutableMapOf(
            "defined" to UDM.Scalar("value"),
            "null" to UDM.Scalar(null)
        ))
        
        val keys = ObjectFunctions.keys(listOf(obj)) as UDM.Array
        assertEquals(2, keys.elements.size, "Should include keys with null values")
    }
}

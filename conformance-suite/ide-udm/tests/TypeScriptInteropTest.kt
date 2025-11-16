package org.apache.utlx.core.udm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tests round-trip compatibility between Kotlin and TypeScript UDM implementations
 *
 * These tests ensure that:
 * 1. Kotlin-serialized .udm can be parsed by TypeScript
 * 2. TypeScript-serialized .udm can be parsed by Kotlin
 * 3. Round-trip (Kotlin → TypeScript → Kotlin) preserves data
 * 4. Path resolution works identically in both implementations
 *
 * The tests use a bridge script (kotlin-roundtrip-bridge.ts) that:
 * - Reads .udm format from stdin
 * - Parses it with TypeScript parser
 * - Serializes it back with TypeScript serializer
 * - Outputs result to stdout
 */
class TypeScriptInteropTest {

    private val bridgeScript = File("../../theia-extension/utlx-theia-extension/lib/browser/udm/__tests__/kotlin-roundtrip-bridge.js")

    /**
     * Execute the TypeScript bridge: Kotlin .udm → TS parse → TS serialize → Kotlin .udm
     */
    private fun executeTypeScriptBridge(udmContent: String): String {
        // Check if bridge script exists
        if (!bridgeScript.exists()) {
            throw IllegalStateException(
                "TypeScript bridge not found at: ${bridgeScript.absolutePath}\n" +
                "Run 'yarn build' in theia-extension/utlx-theia-extension first"
            )
        }

        // Execute Node.js with the bridge script
        val process = ProcessBuilder("node", bridgeScript.absolutePath)
            .redirectError(ProcessBuilder.Redirect.INHERIT) // Show stderr for debugging
            .start()

        // Write UDM content to stdin
        process.outputStream.use { it.write(udmContent.toByteArray()) }

        // Read result from stdout
        val result = process.inputStream.bufferedReader().readText()

        // Wait for process to complete
        val exited = process.waitFor(10, TimeUnit.SECONDS)
        if (!exited) {
            process.destroy()
            throw RuntimeException("TypeScript bridge timed out")
        }

        if (process.exitValue() != 0) {
            throw RuntimeException("TypeScript bridge failed with exit code: ${process.exitValue()}")
        }

        return result
    }

    @Test
    fun `test simple scalar round-trip`() {
        // Create simple scalar in Kotlin
        val original = UDM.Scalar("Hello from Kotlin")

        // Serialize to .udm format
        val serialized = original.toUDMLanguage()
        println("Kotlin serialized:\n$serialized")

        // Send through TypeScript bridge
        val tsResult = executeTypeScriptBridge(serialized)
        println("TypeScript re-serialized:\n$tsResult")

        // Parse TypeScript result back in Kotlin
        val parsed = UDMLanguageParser.parse(tsResult)

        // Verify it matches original
        assertTrue(parsed is UDM.Scalar)
        assertEquals("Hello from Kotlin", (parsed as UDM.Scalar).value)
    }

    @Test
    fun `test simple object round-trip`() {
        // Create simple object in Kotlin
        val original = UDM.Object(
            properties = mapOf(
                "name" to UDM.Scalar("Alice"),
                "age" to UDM.Scalar(30),
                "active" to UDM.Scalar(true)
            )
        )

        val serialized = original.toUDMLanguage()
        println("Kotlin serialized:\n$serialized")

        val tsResult = executeTypeScriptBridge(serialized)
        println("TypeScript re-serialized:\n$tsResult")

        val parsed = UDMLanguageParser.parse(tsResult)

        assertTrue(parsed is UDM.Object)
        val obj = parsed as UDM.Object
        assertEquals("Alice", (obj.get("name") as UDM.Scalar).value)
        assertEquals(30, (obj.get("age") as UDM.Scalar).value)
        assertEquals(true, (obj.get("active") as UDM.Scalar).value)
    }

    @Test
    fun `test object with attributes round-trip`() {
        // This is the CRITICAL test: attributes should NOT appear in property paths
        val original = UDM.Object(
            attributes = mapOf("id" to "123", "type" to "customer"),
            properties = mapOf(
                "name" to UDM.Scalar("Bob"),
                "email" to UDM.Scalar("bob@example.com")
            )
        )

        val serialized = original.toUDMLanguage()
        println("Kotlin serialized:\n$serialized")

        val tsResult = executeTypeScriptBridge(serialized)
        println("TypeScript re-serialized:\n$tsResult")

        val parsed = UDMLanguageParser.parse(tsResult)

        assertTrue(parsed is UDM.Object)
        val obj = parsed as UDM.Object

        // Verify attributes preserved
        assertEquals("123", obj.getAttribute("id"))
        assertEquals("customer", obj.getAttribute("type"))

        // Verify properties preserved
        assertEquals("Bob", (obj.get("name") as UDM.Scalar).value)
        assertEquals("bob@example.com", (obj.get("email") as UDM.Scalar).value)

        // CRITICAL: Verify "attributes" and "properties" are NOT in the properties map
        assertNull(obj.get("attributes"), "Should NOT have 'attributes' as a property")
        assertNull(obj.get("properties"), "Should NOT have 'properties' as a property")
    }

    @Test
    fun `test nested object round-trip`() {
        // Create nested structure that matches real-world use case
        val original = UDM.Object(
            properties = mapOf(
                "providers" to UDM.Object(
                    properties = mapOf(
                        "address" to UDM.Object(
                            properties = mapOf(
                                "street" to UDM.Scalar("123 Main St"),
                                "city" to UDM.Scalar("Boston"),
                                "zip" to UDM.Scalar("02101")
                            )
                        )
                    )
                )
            )
        )

        val serialized = original.toUDMLanguage()
        println("Kotlin serialized:\n$serialized")

        val tsResult = executeTypeScriptBridge(serialized)
        println("TypeScript re-serialized:\n$tsResult")

        val parsed = UDMLanguageParser.parse(tsResult)

        assertTrue(parsed is UDM.Object)
        val obj = parsed as UDM.Object

        // Test path navigation (CLI-style)
        val providers = obj.get("providers") as UDM.Object
        val address = providers.get("address") as UDM.Object
        val street = address.get("street") as UDM.Scalar

        assertEquals("123 Main St", street.value)

        // CRITICAL: These paths should work (without "properties" keyword)
        assertNotNull(obj.get("providers"))
        assertNotNull((obj.get("providers") as UDM.Object).get("address"))

        // These should NOT work (IDE was incorrectly adding "properties")
        assertNull(obj.get("properties"), "Should NOT navigate via 'properties' keyword")
    }

    @Test
    fun `test array round-trip`() {
        val original = UDM.Array(
            listOf(
                UDM.Scalar(1),
                UDM.Scalar(2),
                UDM.Scalar(3),
                UDM.Scalar("four")
            )
        )

        val serialized = original.toUDMLanguage()
        println("Kotlin serialized:\n$serialized")

        val tsResult = executeTypeScriptBridge(serialized)
        println("TypeScript re-serialized:\n$tsResult")

        val parsed = UDMLanguageParser.parse(tsResult)

        assertTrue(parsed is UDM.Array)
        val arr = parsed as UDM.Array
        assertEquals(4, arr.elements.size)
        assertEquals(1, (arr.elements[0] as UDM.Scalar).value)
        assertEquals("four", (arr.elements[3] as UDM.Scalar).value)
    }

    @Test
    fun `test array of objects round-trip`() {
        val original = UDM.Object(
            properties = mapOf(
                "items" to UDM.Array(
                    listOf(
                        UDM.Object(
                            properties = mapOf(
                                "name" to UDM.Scalar("Item 1"),
                                "price" to UDM.Scalar(10.99)
                            )
                        ),
                        UDM.Object(
                            properties = mapOf(
                                "name" to UDM.Scalar("Item 2"),
                                "price" to UDM.Scalar(20.50)
                            )
                        )
                    )
                )
            )
        )

        val serialized = original.toUDMLanguage()
        println("Kotlin serialized:\n$serialized")

        val tsResult = executeTypeScriptBridge(serialized)
        println("TypeScript re-serialized:\n$tsResult")

        val parsed = UDMLanguageParser.parse(tsResult)

        assertTrue(parsed is UDM.Object)
        val obj = parsed as UDM.Object

        val items = obj.get("items") as UDM.Array
        assertEquals(2, items.elements.size)

        val item1 = items.elements[0] as UDM.Object
        assertEquals("Item 1", (item1.get("name") as UDM.Scalar).value)
        assertEquals(10.99, (item1.get("price") as UDM.Scalar).value)
    }

    @Test
    fun `test DateTime types round-trip`() {
        val original = UDM.Object(
            properties = mapOf(
                "timestamp" to UDM.DateTime(java.time.Instant.parse("2024-01-15T10:30:00Z")),
                "date" to UDM.Date(java.time.LocalDate.parse("2024-01-15")),
                "time" to UDM.Time(java.time.LocalTime.parse("10:30:00"))
            )
        )

        val serialized = original.toUDMLanguage()
        println("Kotlin serialized:\n$serialized")

        val tsResult = executeTypeScriptBridge(serialized)
        println("TypeScript re-serialized:\n$tsResult")

        val parsed = UDMLanguageParser.parse(tsResult)

        assertTrue(parsed is UDM.Object)
        val obj = parsed as UDM.Object

        assertTrue(obj.get("timestamp") is UDM.DateTime)
        assertTrue(obj.get("date") is UDM.Date)
        assertTrue(obj.get("time") is UDM.Time)
    }

    @Test
    fun `test real-world example from docs`() {
        // This matches the example from docs/architects/udm-parsing-at-ide.md
        val original = UDM.Object(
            attributes = mapOf("version" to "1.0"),
            properties = mapOf(
                "providers" to UDM.Array(
                    listOf(
                        UDM.Object(
                            attributes = mapOf("id" to "P001"),
                            properties = mapOf(
                                "name" to UDM.Scalar("Dr. Smith"),
                                "address" to UDM.Object(
                                    properties = mapOf(
                                        "street" to UDM.Scalar("123 Medical Plaza"),
                                        "city" to UDM.Scalar("Boston")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val serialized = original.toUDMLanguage()
        println("Kotlin serialized:\n$serialized")

        val tsResult = executeTypeScriptBridge(serialized)
        println("TypeScript re-serialized:\n$tsResult")

        val parsed = UDMLanguageParser.parse(tsResult)

        assertTrue(parsed is UDM.Object)
        val obj = parsed as UDM.Object

        // Verify top-level attribute
        assertEquals("1.0", obj.getAttribute("version"))

        // Navigate: providers[0].address.street
        val providers = obj.get("providers") as UDM.Array
        val provider = providers.elements[0] as UDM.Object
        val address = provider.get("address") as UDM.Object
        val street = address.get("street") as UDM.Scalar

        assertEquals("123 Medical Plaza", street.value)

        // Verify attribute on array element
        assertEquals("P001", provider.getAttribute("id"))

        // CRITICAL PATH TEST: This path should work
        // $input.providers[0].address.street
        // NOT: $input.properties.providers[0].properties.address.properties.street
        assertNotNull(obj.get("providers"))
        assertNull(obj.get("properties"), "Path should NOT include 'properties' keyword")
    }
}

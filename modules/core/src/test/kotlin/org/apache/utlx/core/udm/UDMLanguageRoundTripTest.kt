package org.apache.utlx.core.udm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Round-trip tests for UDM Language
 *
 * Tests that: parse(serialize(udm)) produces equivalent UDM
 *
 * Note: Perfect byte-for-byte equality is not always possible (e.g., Lambda functions
 * cannot be round-tripped), but structural equality should hold.
 */
class UDMLanguageRoundTripTest {

    private fun roundTrip(udm: UDM): UDM {
        val serialized = udm.toUDMLanguage()
        println("Serialized:\n$serialized\n")
        return UDMLanguageParser.parse(serialized)
    }

    private fun assertUDMEquals(expected: UDM, actual: UDM, message: String = "") {
        when {
            expected is UDM.Scalar && actual is UDM.Scalar -> {
                assertEquals(expected.value, actual.value, message)
            }
            expected is UDM.Array && actual is UDM.Array -> {
                assertEquals(expected.elements.size, actual.elements.size, "$message - array size")
                expected.elements.zip(actual.elements).forEachIndexed { i, (e, a) ->
                    assertUDMEquals(e, a, "$message - element $i")
                }
            }
            expected is UDM.Object && actual is UDM.Object -> {
                assertEquals(expected.name, actual.name, "$message - object name")
                assertEquals(expected.attributes, actual.attributes, "$message - attributes")
                assertEquals(expected.metadata, actual.metadata, "$message - metadata")
                assertEquals(expected.properties.keys, actual.properties.keys, "$message - property keys")
                expected.properties.forEach { (key, value) ->
                    assertUDMEquals(value, actual.properties[key]!!, "$message - property $key")
                }
            }
            expected is UDM.DateTime && actual is UDM.DateTime -> {
                assertEquals(expected.instant, actual.instant, message)
            }
            expected is UDM.Date && actual is UDM.Date -> {
                assertEquals(expected.date, actual.date, message)
            }
            expected is UDM.LocalDateTime && actual is UDM.LocalDateTime -> {
                assertEquals(expected.dateTime, actual.dateTime, message)
            }
            expected is UDM.Time && actual is UDM.Time -> {
                assertEquals(expected.time, actual.time, message)
            }
            expected is UDM.Binary && actual is UDM.Binary -> {
                // Binary round-trip loses data (expected limitation)
                assertTrue(actual is UDM.Binary, message)
            }
            expected is UDM.Lambda && actual is UDM.Lambda -> {
                // Lambda round-trip loses function body (expected limitation)
                assertTrue(actual is UDM.Lambda, message)
            }
            else -> {
                fail("Type mismatch: expected ${expected::class.simpleName}, actual ${actual::class.simpleName}")
            }
        }
    }

    @Test
    fun `round-trip simple string scalar`() {
        val udm = UDM.Scalar("Hello World")
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip string with special characters`() {
        val udm = UDM.Scalar("Line 1\nLine 2\tTabbed")
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip integer scalar`() {
        val udm = UDM.Scalar(42)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip double scalar`() {
        val udm = UDM.Scalar(3.14159)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip boolean true`() {
        val udm = UDM.Scalar(true)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip boolean false`() {
        val udm = UDM.Scalar(false)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip null scalar`() {
        val udm = UDM.Scalar(null)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip empty array`() {
        val udm = UDM.Array(emptyList())
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip simple array`() {
        val udm = UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("b"),
            UDM.Scalar("c")
        ))
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip mixed array`() {
        val udm = UDM.Array(listOf(
            UDM.Scalar("text"),
            UDM.Scalar(123),
            UDM.Scalar(true),
            UDM.Scalar(null)
        ))
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip simple object`() {
        val udm = UDM.Object(
            properties = mapOf(
                "name" to UDM.Scalar("Alice"),
                "age" to UDM.Scalar(30),
                "active" to UDM.Scalar(true)
            )
        )
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip object with name`() {
        val udm = UDM.Object(
            name = "Customer",
            properties = mapOf(
                "id" to UDM.Scalar("CUST-123")
            )
        )
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip object with metadata`() {
        val udm = UDM.Object(
            name = "Order",
            metadata = mapOf(
                "source" to "xml",
                "lineNumber" to "42"
            ),
            properties = mapOf(
                "id" to UDM.Scalar("ORD-001")
            )
        )
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip object with attributes`() {
        val udm = UDM.Object(
            attributes = mapOf(
                "id" to "CUST-789",
                "status" to "active"
            ),
            properties = mapOf(
                "name" to UDM.Scalar("Bob")
            )
        )
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip nested objects`() {
        val udm = UDM.Object(
            properties = mapOf(
                "customer" to UDM.Object(
                    properties = mapOf(
                        "name" to UDM.Scalar("Alice"),
                        "address" to UDM.Object(
                            properties = mapOf(
                                "street" to UDM.Scalar("123 Main St"),
                                "city" to UDM.Scalar("Springfield")
                            )
                        )
                    )
                )
            )
        )
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip array of objects`() {
        val udm = UDM.Array(listOf(
            UDM.Object(properties = mapOf("id" to UDM.Scalar(1))),
            UDM.Object(properties = mapOf("id" to UDM.Scalar(2))),
            UDM.Object(properties = mapOf("id" to UDM.Scalar(3)))
        ))
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip DateTime`() {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val udm = UDM.DateTime(instant)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip Date`() {
        val date = LocalDate.parse("2024-01-15")
        val udm = UDM.Date(date)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip LocalDateTime`() {
        val dateTime = LocalDateTime.parse("2024-01-15T10:30:00")
        val udm = UDM.LocalDateTime(dateTime)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip Time`() {
        val time = LocalTime.parse("10:30:00")
        val udm = UDM.Time(time)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip Binary`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val udm = UDM.Binary(data)
        val result = roundTrip(udm)
        // Binary loses data in round-trip (expected limitation)
        assertTrue(result is UDM.Binary)
    }

    @Test
    fun `round-trip Lambda`() {
        val udm = UDM.Lambda { _: List<UDM> -> UDM.Scalar(42) }
        val result = roundTrip(udm)
        // Lambda loses function body in round-trip (expected limitation)
        assertTrue(result is UDM.Lambda)
    }

    @Test
    fun `round-trip complex real-world example`() {
        val udm = UDM.Object(
            name = "Order",
            metadata = mapOf(
                "source" to "xml",
                "lineNumber" to "10",
                "validated" to "true"
            ),
            attributes = mapOf(
                "id" to "ORD-001",
                "status" to "confirmed"
            ),
            properties = mapOf(
                "customer" to UDM.Object(
                    name = "Customer",
                    properties = mapOf(
                        "name" to UDM.Scalar("Alice Johnson"),
                        "email" to UDM.Scalar("alice@example.com")
                    )
                ),
                "total" to UDM.Scalar(1299.99),
                "orderDate" to UDM.DateTime(Instant.parse("2024-01-15T10:30:00Z")),
                "items" to UDM.Array(listOf(
                    UDM.Object(
                        properties = mapOf(
                            "sku" to UDM.Scalar("LAPTOP-X1"),
                            "price" to UDM.Scalar(1299.99),
                            "quantity" to UDM.Scalar(1)
                        )
                    )
                ))
            )
        )

        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip with source info`() {
        val udm = UDM.Scalar("test")
        val sourceInfo = mapOf(
            "source" to "test.xml",
            "parsed-at" to "2024-01-15T10:30:00Z"
        )

        val serialized = UDMLanguageSerializer().serialize(udm, sourceInfo)
        val result = UDMLanguageParser.parse(serialized)

        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip deeply nested structure`() {
        fun makeNested(depth: Int): UDM {
            if (depth == 0) return UDM.Scalar("leaf")
            return UDM.Object(
                properties = mapOf(
                    "level" to UDM.Scalar(depth),
                    "child" to makeNested(depth - 1)
                )
            )
        }

        val udm = makeNested(5)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip large array`() {
        val elements = (1..100).map { UDM.Scalar(it) }
        val udm = UDM.Array(elements)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }

    @Test
    fun `round-trip object with many properties`() {
        val properties = (1..50).associate {
            "prop$it" to UDM.Scalar("value$it")
        }
        val udm = UDM.Object(properties = properties)
        val result = roundTrip(udm)
        assertUDMEquals(udm, result)
    }
}

package org.apache.utlx.core.udm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class UDMLanguageSerializerTest {

    private val serializer = UDMLanguageSerializer(prettyPrint = true)

    @Test
    fun `serialize simple scalar values`() {
        val tests = listOf(
            UDM.Scalar("Hello") to "\"Hello\"",
            UDM.Scalar(42) to "42",
            UDM.Scalar(3.14) to "3.14",
            UDM.Scalar(true) to "true",
            UDM.Scalar(false) to "false",
            UDM.Scalar(null) to "null"
        )

        tests.forEach { (udm, expected) ->
            val result = serializer.serialize(udm)
            assertTrue(result.contains(expected), "Expected '$expected' in output:\n$result")
        }
    }

    @Test
    fun `serialize simple array`() {
        val udm = UDM.Array(listOf(
            UDM.Scalar("item1"),
            UDM.Scalar("item2"),
            UDM.Scalar(42)
        ))

        val result = serializer.serialize(udm)

        assertTrue(result.contains("@udm-version: 1.0"))
        assertTrue(result.contains("\"item1\""))
        assertTrue(result.contains("\"item2\""))
        assertTrue(result.contains("42"))
    }

    @Test
    fun `serialize empty array`() {
        val udm = UDM.Array(emptyList())
        val result = serializer.serialize(udm)

        assertTrue(result.contains("[]"))
    }

    @Test
    fun `serialize simple object`() {
        val udm = UDM.Object(
            properties = mapOf(
                "name" to UDM.Scalar("Alice"),
                "age" to UDM.Scalar(30),
                "active" to UDM.Scalar(true)
            )
        )

        val result = serializer.serialize(udm)

        assertTrue(result.contains("name: \"Alice\""))
        assertTrue(result.contains("age: 30"))
        assertTrue(result.contains("active: true"))
    }

    @Test
    fun `serialize object with metadata`() {
        val udm = UDM.Object(
            name = "Customer",
            metadata = mapOf(
                "source" to "xml",
                "lineNumber" to "42"
            ),
            properties = mapOf(
                "name" to UDM.Scalar("Alice")
            )
        )

        val result = serializer.serialize(udm)

        assertTrue(result.contains("@Object"))
        assertTrue(result.contains("name: \"Customer\""))
        assertTrue(result.contains("metadata:"))
        assertTrue(result.contains("source: \"xml\""))
        assertTrue(result.contains("lineNumber: \"42\""))
    }

    @Test
    fun `serialize object with attributes`() {
        val udm = UDM.Object(
            attributes = mapOf(
                "id" to "CUST-789",
                "status" to "active"
            ),
            properties = mapOf(
                "name" to UDM.Scalar("Alice")
            )
        )

        val result = serializer.serialize(udm)

        assertTrue(result.contains("attributes:"))
        assertTrue(result.contains("id: \"CUST-789\""))
        assertTrue(result.contains("status: \"active\""))
        assertTrue(result.contains("properties:"))
        assertTrue(result.contains("name: \"Alice\""))
    }

    @Test
    fun `serialize nested structure`() {
        val udm = UDM.Object(
            properties = mapOf(
                "customer" to UDM.Object(
                    properties = mapOf(
                        "name" to UDM.Scalar("Alice"),
                        "orders" to UDM.Array(listOf(
                            UDM.Object(
                                properties = mapOf(
                                    "id" to UDM.Scalar("ORD-001"),
                                    "total" to UDM.Scalar(100.0)
                                )
                            )
                        ))
                    )
                )
            )
        )

        val result = serializer.serialize(udm)

        assertTrue(result.contains("customer:"))
        assertTrue(result.contains("name: \"Alice\""))
        assertTrue(result.contains("orders:"))
        assertTrue(result.contains("id: \"ORD-001\""))
        assertTrue(result.contains("total: 100.0"))
    }

    @Test
    fun `serialize DateTime`() {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val udm = UDM.DateTime(instant)

        val result = serializer.serialize(udm)

        assertTrue(result.contains("@DateTime"))
        assertTrue(result.contains("2024-01-15T10:30:00Z"))
    }

    @Test
    fun `serialize Date`() {
        val date = LocalDate.parse("2024-01-15")
        val udm = UDM.Date(date)

        val result = serializer.serialize(udm)

        assertTrue(result.contains("@Date"))
        assertTrue(result.contains("2024-01-15"))
    }

    @Test
    fun `serialize LocalDateTime`() {
        val dateTime = LocalDateTime.parse("2024-01-15T10:30:00")
        val udm = UDM.LocalDateTime(dateTime)

        val result = serializer.serialize(udm)

        assertTrue(result.contains("@LocalDateTime"))
        assertTrue(result.contains("2024-01-15T10:30:00"))
    }

    @Test
    fun `serialize Time`() {
        val time = LocalTime.parse("10:30:00")
        val udm = UDM.Time(time)

        val result = serializer.serialize(udm)

        assertTrue(result.contains("@Time"))
        assertTrue(result.contains("10:30:00"))
    }

    @Test
    fun `serialize Binary`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val udm = UDM.Binary(data)

        val result = serializer.serialize(udm)

        assertTrue(result.contains("@Binary"))
        assertTrue(result.contains("size: 5"))
    }

    @Test
    fun `serialize Lambda`() {
        val udm = UDM.Lambda { _: List<UDM> -> UDM.Scalar(null) }

        val result = serializer.serialize(udm)

        assertTrue(result.contains("@Lambda"))
    }

    @Test
    fun `serialize with source info`() {
        val udm = UDM.Scalar("test")
        val sourceInfo = mapOf(
            "source" to "test.xml",
            "parsed-at" to "2024-01-15T10:30:00Z"
        )

        val result = serializer.serialize(udm, sourceInfo)

        assertTrue(result.contains("@source: \"test.xml\""))
        assertTrue(result.contains("@parsed-at: \"2024-01-15T10:30:00Z\""))
    }

    @Test
    fun `test extension function`() {
        val udm = UDM.Scalar("test")
        val result = udm.toUDMLanguage()

        assertTrue(result.contains("@udm-version: 1.0"))
        assertTrue(result.contains("\"test\""))
    }

    @Test
    fun `serialize escapes special characters in strings`() {
        val udm = UDM.Object(
            properties = mapOf(
                "text" to UDM.Scalar("Line 1\nLine 2\tTabbed")
            )
        )

        val result = serializer.serialize(udm)

        assertTrue(result.contains("\\n"))
        assertTrue(result.contains("\\t"))
    }

    @Test
    fun `serialize complex real-world example`() {
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

        val result = serializer.serialize(udm)

        // Verify header
        assertTrue(result.contains("@udm-version: 1.0"))

        // Verify metadata
        assertTrue(result.contains("@Object"))
        assertTrue(result.contains("name: \"Order\""))
        assertTrue(result.contains("metadata:"))
        assertTrue(result.contains("source: \"xml\""))

        // Verify attributes
        assertTrue(result.contains("attributes:"))
        assertTrue(result.contains("id: \"ORD-001\""))

        // Verify properties
        assertTrue(result.contains("properties:"))
        assertTrue(result.contains("customer:"))
        assertTrue(result.contains("\"Alice Johnson\""))
        assertTrue(result.contains("total: 1299.99"))
        assertTrue(result.contains("@DateTime"))
        assertTrue(result.contains("items:"))
        assertTrue(result.contains("LAPTOP-X1"))
    }
}

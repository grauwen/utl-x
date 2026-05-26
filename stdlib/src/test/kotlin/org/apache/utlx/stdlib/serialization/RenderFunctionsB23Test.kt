package org.apache.utlx.stdlib.serialization

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B23: Tests for renderXml, renderYaml, renderCsv — inline serialization functions.
 * These produce format strings for embedding inside other documents (middleware pattern).
 */
class RenderFunctionsB23Test {

    // =========================================================================
    // renderXml(data, pretty?)
    // =========================================================================

    @Test fun `renderXml - produces valid XML`() {
        val obj = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val result = SerializationFunctions.renderXml(listOf(obj))
        val xml = (result as UDM.Scalar).value as String
        assertTrue(xml.contains("<name>Alice</name>"), "Should be XML: $xml")
    }

    @Test fun `renderXml - nested structure`() {
        val obj = UDM.Object.of(
            "person" to UDM.Object.of("name" to UDM.Scalar("John"), "age" to UDM.Scalar(30))
        )
        val result = SerializationFunctions.renderXml(listOf(obj))
        val xml = (result as UDM.Scalar).value as String
        assertTrue(xml.contains("<person>"), "Should contain <person>: $xml")
        assertTrue(xml.contains("<name>John</name>"), "Should contain name: $xml")
        assertTrue(xml.contains("<age>30</age>"), "Should contain age: $xml")
    }

    @Test fun `renderXml - compact by default`() {
        val obj = UDM.Object.of("a" to UDM.Scalar(1))
        val result = SerializationFunctions.renderXml(listOf(obj))
        val xml = (result as UDM.Scalar).value as String
        // Compact: no indentation between tags
        assertTrue(!xml.contains("  <a>"), "Should be compact: $xml")
    }

    @Test fun `renderXml - pretty when requested`() {
        val obj = UDM.Object.of("a" to UDM.Scalar(1), "b" to UDM.Scalar(2))
        val result = SerializationFunctions.renderXml(listOf(obj, UDM.Scalar(true)))
        val xml = (result as UDM.Scalar).value as String
        assertTrue(xml.contains("\n"), "Pretty should have newlines: $xml")
    }

    @Test fun `renderXml - includes xml declaration`() {
        val obj = UDM.Object.of("a" to UDM.Scalar(1))
        val result = SerializationFunctions.renderXml(listOf(obj))
        val xml = (result as UDM.Scalar).value as String
        assertTrue(xml.contains("<?xml"), "Should have declaration: $xml")
    }

    @Test fun `renderXml - unicode preserved`() {
        val obj = UDM.Object.of("city" to UDM.Scalar("München"), "name" to UDM.Scalar("Müller"))
        val result = SerializationFunctions.renderXml(listOf(obj))
        val xml = (result as UDM.Scalar).value as String
        assertTrue(xml.contains("München"), "Should preserve unicode: $xml")
        assertTrue(xml.contains("Müller"), "Should preserve unicode: $xml")
    }

    @Test fun `renderXml - missing args throws`() {
        assertThrows<Exception> { SerializationFunctions.renderXml(emptyList()) }
    }

    // =========================================================================
    // renderYaml(data)
    // =========================================================================

    @Test fun `renderYaml - produces valid YAML`() {
        val obj = UDM.Object.of("name" to UDM.Scalar("Alice"), "age" to UDM.Scalar(30))
        val result = SerializationFunctions.renderYaml(listOf(obj))
        val yaml = (result as UDM.Scalar).value as String
        assertTrue(yaml.contains("name:"), "Should have key: $yaml")
        assertTrue(yaml.contains("Alice"), "Should have value: $yaml")
    }

    @Test fun `renderYaml - nested structure`() {
        val obj = UDM.Object.of("person" to UDM.Object.of("name" to UDM.Scalar("Bob")))
        val result = SerializationFunctions.renderYaml(listOf(obj))
        val yaml = (result as UDM.Scalar).value as String
        assertTrue(yaml.contains("person:"), "Should contain person: $yaml")
        assertTrue(yaml.contains("name:"), "Should contain name: $yaml")
        assertTrue(yaml.contains("Bob"), "Should contain Bob: $yaml")
    }

    @Test fun `renderYaml - unicode preserved`() {
        val obj = UDM.Object.of("city" to UDM.Scalar("Zürich"))
        val result = SerializationFunctions.renderYaml(listOf(obj))
        val yaml = (result as UDM.Scalar).value as String
        assertTrue(yaml.contains("Zürich"), "Should preserve unicode: $yaml")
    }

    @Test fun `renderYaml - missing args throws`() {
        assertThrows<Exception> { SerializationFunctions.renderYaml(emptyList()) }
    }

    // =========================================================================
    // renderCsv(data, includeHeaders?)
    // =========================================================================

    @Test fun `renderCsv - produces CSV with headers by default`() {
        val arr = UDM.Array(listOf(
            UDM.Object.of("name" to UDM.Scalar("Alice"), "age" to UDM.Scalar(30)),
            UDM.Object.of("name" to UDM.Scalar("Bob"), "age" to UDM.Scalar(25))
        ))
        val result = SerializationFunctions.renderCsv(listOf(arr))
        val csv = (result as UDM.Scalar).value as String
        assertTrue(csv.contains("name"), "Should have header: $csv")
        assertTrue(csv.contains("Alice"), "Should have data: $csv")
        assertTrue(csv.contains("Bob"), "Should have data: $csv")
        // First line should be headers
        val lines = csv.trim().lines()
        assertTrue(lines.size >= 3, "Should have header + 2 rows: $csv")
    }

    @Test fun `renderCsv - without headers`() {
        val arr = UDM.Array(listOf(UDM.Object.of("name" to UDM.Scalar("Alice"), "age" to UDM.Scalar(30))))
        val result = SerializationFunctions.renderCsv(listOf(arr, UDM.Scalar(false)))
        val csv = (result as UDM.Scalar).value as String
        val lines = csv.trim().lines()
        assertEquals(1, lines.size, "Should have 1 row only (no header): $csv")
        assertTrue(csv.contains("Alice"), "Should have data: $csv")
    }

    @Test fun `renderCsv - single row`() {
        val arr = UDM.Array(listOf(UDM.Object.of("x" to UDM.Scalar(1), "y" to UDM.Scalar(2))))
        val result = SerializationFunctions.renderCsv(listOf(arr))
        val csv = (result as UDM.Scalar).value as String
        assertTrue(csv.contains("x"), "Should have header: $csv")
    }

    @Test fun `renderCsv - missing args throws`() {
        assertThrows<Exception> { SerializationFunctions.renderCsv(emptyList()) }
    }

    // =========================================================================
    // Middleware pattern: embed serialized format inside JSON field
    // =========================================================================

    @Test fun `middleware pattern - XML string in JSON field`() {
        val data = UDM.Object.of("name" to UDM.Scalar("Müller"), "amount" to UDM.Scalar(1234.56))
        val result = SerializationFunctions.renderXml(listOf(data))
        val xml = (result as UDM.Scalar).value as String
        // The XML string should be embeddable in a JSON field
        assertTrue(xml.startsWith("<?xml"), "Should be valid XML: $xml")
        assertTrue(xml.contains("Müller"), "Should preserve special chars: $xml")
    }

    @Test fun `middleware pattern - YAML config string in JSON field`() {
        val config = UDM.Object.of("host" to UDM.Scalar("localhost"), "port" to UDM.Scalar(8080))
        val result = SerializationFunctions.renderYaml(listOf(config))
        val yaml = (result as UDM.Scalar).value as String
        assertTrue(yaml.contains("host:"), "Should be valid YAML: $yaml")
        assertTrue(yaml.contains("8080"), "Should have port: $yaml")
    }
}

package org.apache.utlx.formats.jsch

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Minimal unit tests for USDL to JSON Schema transformation internal logic.
 *
 * Feature tests are in conformance-suite/tests/formats/jsch/usdl/
 *
 * These tests verify internal API behavior that cannot be tested via CLI.
 */
class USDLToJSONSchemaTest {

    @Test
    fun `serialize USDL schema via internal API`() {
        // Given: Basic USDL schema using UDM API
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("id"),
                                            "%type" to UDM.Scalar("string"),
                                            "%required" to UDM.Scalar(true)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize via internal API
        val serializer = JSONSchemaSerializer(prettyPrint = false, strict = false)
        val jsonSchema = serializer.serialize(usdlSchema)

        // Then: Should produce valid JSON Schema
        assertTrue(jsonSchema.contains("\"\$schema\""))
        assertTrue(jsonSchema.contains("\"\$defs\""))
        assertTrue(jsonSchema.contains("\"Customer\""))
        assertTrue(jsonSchema.contains("\"type\":\"object\""))
        assertTrue(jsonSchema.contains("\"required\":[\"id\"]"))
    }

    @Test
    fun `enumeration type via internal API`() {
        // Given: USDL enumeration
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Status" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("enumeration"),
                                "%values" to UDM.Array(
                                    elements = listOf(
                                        UDM.Scalar("pending"),
                                        UDM.Scalar("approved")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize via internal API
        val serializer = JSONSchemaSerializer(prettyPrint = false, strict = false)
        val jsonSchema = serializer.serialize(usdlSchema)

        // Then: Should produce enum schema
        assertTrue(jsonSchema.contains("\"Status\""))
        assertTrue(jsonSchema.contains("\"type\":\"string\""))
        assertTrue(jsonSchema.contains("\"enum\""))
        assertTrue(jsonSchema.contains("\"pending\""))
    }
}

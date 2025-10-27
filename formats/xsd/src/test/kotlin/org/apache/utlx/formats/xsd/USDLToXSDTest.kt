package org.apache.utlx.formats.xsd

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Minimal unit tests for USDL to XSD transformation internal logic.
 *
 * Feature tests are in conformance-suite/tests/formats/xsd/usdl/
 *
 * These tests verify internal API behavior that cannot be tested via CLI.
 */
class USDLToXSDTest {

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
        val serializer = XSDSerializer(prettyPrint = false)
        val xsd = serializer.serialize(usdlSchema)

        // Then: Should produce valid XSD
        assertTrue(xsd.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xsd.contains("<xs:schema"))
        assertTrue(xsd.contains("<xs:complexType name=\"Customer\">"))
        assertTrue(xsd.contains("<xs:element name=\"id\" type=\"xs:string\"/>"))
    }

    @Test
    fun `error when types directive has invalid structure`() {
        // Given: %types is a scalar instead of object (invalid)
        val invalidSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Scalar("invalid")
            )
        )

        // When/Then: Should throw exception
        val serializer = XSDSerializer()
        val exception = assertThrows(Exception::class.java) {
            serializer.serialize(invalidSchema)
        }

        // Verify error is about invalid %types structure
        assertTrue(
            exception.message?.contains("UDM must be Object type") == true ||
            exception is ClassCastException ||
            exception.message?.contains("USDL schema requires") == true
        )
    }
}

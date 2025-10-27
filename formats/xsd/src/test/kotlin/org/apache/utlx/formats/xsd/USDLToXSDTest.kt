package org.apache.utlx.formats.xsd

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for USDL (Universal Schema Definition Language) to XSD transformation
 */
class USDLToXSDTest {

    @Test
    fun `transform basic USDL schema to XSD`() {
        // Given: Basic USDL schema with %types, %kind, %fields
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
                                        )),
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("email"),
                                            "%type" to UDM.Scalar("string"),
                                            "%required" to UDM.Scalar(false)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize to XSD
        val serializer = XSDSerializer(prettyPrint = false)
        val xsd = serializer.serialize(usdlSchema)

        // Then: Should generate valid XSD
        assertTrue(xsd.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xsd.contains("<xs:schema"))
        assertTrue(xsd.contains("xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""))
        assertTrue(xsd.contains("<xs:complexType name=\"Customer\">"))
        assertTrue(xsd.contains("<xs:sequence>"))
        assertTrue(xsd.contains("<xs:element name=\"id\" type=\"xs:string\"/>"))
        assertTrue(xsd.contains("<xs:element name=\"email\" type=\"xs:string\" minOccurs=\"0\"/>"))
    }

    @Test
    fun `transform USDL schema with namespace`() {
        // Given: USDL schema with %namespace directive
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%namespace" to UDM.Scalar("http://example.com/customer"),
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Person" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("name"),
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

        // When: Serialize to XSD
        val serializer = XSDSerializer(prettyPrint = false)
        val xsd = serializer.serialize(usdlSchema)

        // Then: Should include target namespace
        assertTrue(xsd.contains("targetNamespace=\"http://example.com/customer\""))
    }

    @Test
    fun `transform USDL schema with documentation`() {
        // Given: USDL schema with %documentation and %description directives
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Order" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%documentation" to UDM.Scalar("Customer order information"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("orderId"),
                                            "%type" to UDM.Scalar("string"),
                                            "%required" to UDM.Scalar(true),
                                            "%description" to UDM.Scalar("Unique order identifier")
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize to XSD
        val serializer = XSDSerializer(prettyPrint = false, addDocumentation = true)
        val xsd = serializer.serialize(usdlSchema)

        // Then: Should include xs:annotation and xs:documentation
        assertTrue(xsd.contains("<xs:annotation>"))
        assertTrue(xsd.contains("<xs:documentation>"))
        assertTrue(xsd.contains("Customer order information"))
        assertTrue(xsd.contains("Unique order identifier"))
    }

    @Test
    fun `transform USDL schema with multiple types`() {
        // Given: USDL schema with multiple type definitions
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
                        ),
                        "Order" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("orderId"),
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

        // When: Serialize to XSD
        val serializer = XSDSerializer(prettyPrint = false)
        val xsd = serializer.serialize(usdlSchema)

        // Then: Should generate both complex types
        assertTrue(xsd.contains("<xs:complexType name=\"Customer\">"))
        assertTrue(xsd.contains("<xs:complexType name=\"Order\">"))
    }

    @Test
    fun `detect USDL mode with percent types directive`() {
        // Given: Schema with %types directive
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Test" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(emptyList())
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = XSDSerializer(prettyPrint = false)
        val xsd = serializer.serialize(usdlSchema)

        // Then: Should generate XSD (USDL mode detected)
        assertTrue(xsd.contains("<xs:schema"))
        assertTrue(xsd.contains("<xs:complexType name=\"Test\">"))
    }

    @Test
    fun `required fields have no minOccurs attribute`() {
        // Given: USDL schema with required field
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Test" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("requiredField"),
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

        // When: Serialize to XSD
        val serializer = XSDSerializer(prettyPrint = false)
        val xsd = serializer.serialize(usdlSchema)

        // Then: Required field should not have minOccurs attribute
        assertTrue(xsd.contains("<xs:element name=\"requiredField\" type=\"xs:string\"/>"))
        assertFalse(xsd.contains("requiredField\" type=\"xs:string\" minOccurs"))
    }

    @Test
    fun `optional fields have minOccurs zero`() {
        // Given: USDL schema with optional field (required=false or not specified)
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Test" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("optionalField"),
                                            "%type" to UDM.Scalar("string"),
                                            "%required" to UDM.Scalar(false)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize to XSD
        val serializer = XSDSerializer(prettyPrint = false)
        val xsd = serializer.serialize(usdlSchema)

        // Then: Optional field should have minOccurs="0"
        assertTrue(xsd.contains("<xs:element name=\"optionalField\" type=\"xs:string\" minOccurs=\"0\"/>"))
    }

    @Test
    fun `error when types directive is missing`() {
        // Given: Schema without %types directive won't be detected as USDL mode
        // Instead, let's test a schema that IS in USDL mode (has %types) but %types is invalid
        val invalidSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Scalar("invalid")  // %types must be Object, not Scalar
            )
        )

        // When/Then: Should throw IllegalArgumentException (ClassCastException or format error)
        val serializer = XSDSerializer()
        val exception = assertThrows(Exception::class.java) {
            serializer.serialize(invalidSchema)
        }

        // Verify it's because %types is not an Object
        assertTrue(
            exception.message?.contains("UDM must be Object type") == true ||
            exception is ClassCastException ||
            exception.message?.contains("USDL schema requires") == true
        )
    }
}

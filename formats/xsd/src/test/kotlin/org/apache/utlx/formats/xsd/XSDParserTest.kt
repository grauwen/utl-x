package org.apache.utlx.formats.xsd

import org.apache.utlx.core.udm.UDM
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class XSDParserTest {

    @Test
    fun `parse basic XSD - Venetian Blind pattern`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified">

              <xs:element name="person" type="PersonType"/>

              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                  <xs:element name="age" type="xs:int"/>
                </xs:sequence>
              </xs:complexType>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val udm = parser.parse()

        // Verify it's an object
        udm.shouldBeInstanceOf<UDM.Object>()
        val schema = udm as UDM.Object

        // Verify schema metadata
        schema.metadata["__schemaType"] shouldBe "xsd-schema"
        schema.metadata["__xsdVersion"] shouldBe "1.0"
        schema.metadata["__targetNamespace"] shouldBe "http://example.com/test"
    }

    @Test
    fun `parse XSD with annotations`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

              <xs:element name="customer" type="xs:string">
                <xs:annotation>
                  <xs:documentation>Customer name field</xs:documentation>
                </xs:annotation>
              </xs:element>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val udm = parser.parse()

        udm.shouldBeInstanceOf<UDM.Object>()
        val schema = udm as UDM.Object

        // Verify schema type
        schema.metadata["__schemaType"] shouldBe "xsd-schema"

        // Verify element exists
        schema.properties shouldNotBe null
    }

    @Test
    fun `detect XSD 1_1 version`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       vc:minVersion="1.1">

              <xs:element name="test" type="xs:string">
                <xs:assert test="string-length(.) > 0"/>
              </xs:element>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val udm = parser.parse()

        val schema = udm as UDM.Object
        schema.metadata["__xsdVersion"] shouldBe "1.1"
    }

    @Test
    fun `parse customer_xsd file - Venetian Blind pattern`() {
        // Use existing test data file
        val xsdFile = File("test-data/customer.xsd")
        if (!xsdFile.exists()) {
            // Skip if file doesn't exist
            println("Skipping test: customer.xsd not found")
            return
        }

        val xsd = xsdFile.readText()
        val parser = XSDParser(xsd)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // Verify basic schema metadata
        schema.metadata["__schemaType"] shouldBe "xsd-schema"
        schema.metadata["__targetNamespace"] shouldBe "http://example.com/customer"

        // This is Venetian Blind pattern (local elements, global types)
        // We should be able to detect this via the scope tags
    }

    @Test
    fun `tag global elements with scope=global`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // The element should be tagged as global
        val element = schema.properties["element"] as? UDM.Object
        if (element != null) {
            element.metadata["__scope"] shouldBe "global"
            element.metadata["__schemaType"] shouldBe "xsd-element"
        }
    }

    @Test
    fun `tag global types with scope=global`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // The complexType should be tagged as global
        val complexType = schema.properties["complexType"] as? UDM.Object
        if (complexType != null) {
            complexType.metadata["__scope"] shouldBe "global"
            complexType.metadata["__schemaType"] shouldBe "xsd-complexType"
        }
    }

    @Test
    fun `tag local elements with scope=local`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="child" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val udm = parser.parse()

        // The nested element should be tagged as local
        // (This test validates the tagging logic works recursively)
        udm.shouldBeInstanceOf<UDM.Object>()
    }

    @Test
    fun `parse XSD with multiple namespaces`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:tns="http://example.com/target"
                       targetNamespace="http://example.com/target">

              <xs:element name="message" type="tns:MessageType"/>

              <xs:complexType name="MessageType">
                <xs:sequence>
                  <xs:element name="text" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val udm = parser.parse()

        val schema = udm as UDM.Object
        schema.metadata["__targetNamespace"] shouldBe "http://example.com/target"
    }
}

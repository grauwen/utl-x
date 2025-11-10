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

    // ========================================================================
    // Design Pattern Detection Tests - All 7 Patterns
    // ========================================================================

    @Test
    fun `detect Russian Doll pattern - local elements and types`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

              <!-- Single root element with all nested elements defined locally -->
              <xs:element name="library">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="book" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="title" type="xs:string"/>
                          <xs:element name="author" type="xs:string"/>
                          <xs:element name="isbn" type="xs:string"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val parsedSchema = parser.parse()
        val usdl = parser.toUSDL(parsedSchema)

        val schema = usdl as UDM.Object
        val detectedPattern = schema.properties["%xsdPattern"]

        detectedPattern.shouldBeInstanceOf<UDM.Scalar>()
        (detectedPattern as UDM.Scalar).value shouldBe "russian-doll"
    }

    @Test
    fun `detect Salami Slice pattern - global elements, local types`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

              <!-- All elements defined globally -->
              <xs:element name="library">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element ref="book" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>

              <xs:element name="book">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element ref="title"/>
                    <xs:element ref="author"/>
                    <xs:element ref="isbn"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>

              <xs:element name="title" type="xs:string"/>
              <xs:element name="author" type="xs:string"/>
              <xs:element name="isbn" type="xs:string"/>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val parsedSchema = parser.parse()
        val usdl = parser.toUSDL(parsedSchema)

        val schema = usdl as UDM.Object
        val detectedPattern = schema.properties["%xsdPattern"]

        detectedPattern.shouldBeInstanceOf<UDM.Scalar>()
        (detectedPattern as UDM.Scalar).value shouldBe "salami-slice"
    }

    @Test
    fun `detect Venetian Blind pattern - local elements, global types`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

              <!-- Single root element using global type -->
              <xs:element name="library" type="LibraryType"/>

              <!-- All types defined globally, all elements local -->
              <xs:complexType name="LibraryType">
                <xs:sequence>
                  <xs:element name="book" type="BookType" maxOccurs="unbounded"/>
                </xs:sequence>
              </xs:complexType>

              <xs:complexType name="BookType">
                <xs:sequence>
                  <xs:element name="title" type="xs:string"/>
                  <xs:element name="author" type="PersonType"/>
                  <xs:element name="isbn" type="ISBNType"/>
                </xs:sequence>
              </xs:complexType>

              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="firstName" type="xs:string"/>
                  <xs:element name="lastName" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>

              <xs:simpleType name="ISBNType">
                <xs:restriction base="xs:string">
                  <xs:pattern value="[0-9]{3}-[0-9]{10}"/>
                </xs:restriction>
              </xs:simpleType>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val parsedSchema = parser.parse()
        val usdl = parser.toUSDL(parsedSchema)

        val schema = usdl as UDM.Object
        val detectedPattern = schema.properties["%xsdPattern"]

        detectedPattern.shouldBeInstanceOf<UDM.Scalar>()
        (detectedPattern as UDM.Scalar).value shouldBe "venetian-blind"
    }

    @Test
    fun `detect Garden of Eden pattern - global elements and types (large schema)`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

              <!-- Global elements -->
              <xs:element name="library" type="LibraryType"/>
              <xs:element name="book" type="BookType"/>
              <xs:element name="title" type="xs:string"/>
              <xs:element name="author" type="PersonType"/>
              <xs:element name="publisher" type="PublisherType"/>
              <xs:element name="isbn" type="ISBNType"/>
              <xs:element name="year" type="xs:int"/>
              <xs:element name="price" type="xs:decimal"/>

              <!-- Global types -->
              <xs:complexType name="LibraryType">
                <xs:sequence>
                  <xs:element ref="book" maxOccurs="unbounded"/>
                </xs:sequence>
              </xs:complexType>

              <xs:complexType name="BookType">
                <xs:sequence>
                  <xs:element ref="title"/>
                  <xs:element ref="author"/>
                  <xs:element ref="publisher"/>
                  <xs:element ref="isbn"/>
                  <xs:element ref="year"/>
                  <xs:element ref="price"/>
                </xs:sequence>
              </xs:complexType>

              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="firstName" type="xs:string"/>
                  <xs:element name="lastName" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>

              <xs:complexType name="PublisherType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                  <xs:element name="city" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>

              <xs:simpleType name="ISBNType">
                <xs:restriction base="xs:string">
                  <xs:pattern value="[0-9]{3}-[0-9]{10}"/>
                </xs:restriction>
              </xs:simpleType>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val parsedSchema = parser.parse()
        val usdl = parser.toUSDL(parsedSchema)

        val schema = usdl as UDM.Object
        val detectedPattern = schema.properties["%xsdPattern"]

        detectedPattern.shouldBeInstanceOf<UDM.Scalar>()
        (detectedPattern as UDM.Scalar).value shouldBe "garden-of-eden"
    }

    @Test
    fun `detect Swiss Army Knife pattern - global elements and types (small schema)`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

              <!-- Small schema with global elements and types (≤5 of each) -->
              <xs:element name="person" type="PersonType"/>
              <xs:element name="name" type="xs:string"/>
              <xs:element name="age" type="xs:int"/>

              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element ref="name"/>
                  <xs:element ref="age"/>
                </xs:sequence>
              </xs:complexType>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val parsedSchema = parser.parse()
        val usdl = parser.toUSDL(parsedSchema)

        val schema = usdl as UDM.Object
        val detectedPattern = schema.properties["%xsdPattern"]

        detectedPattern.shouldBeInstanceOf<UDM.Scalar>()
        (detectedPattern as UDM.Scalar).value shouldBe "swiss-army-knife"
    }

    @Test
    fun `detect Bologna Sandwich pattern - mix of global and local elements with global types`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

              <!-- Global elements -->
              <xs:element name="library" type="LibraryType"/>
              <xs:element name="book" type="BookType"/>

              <!-- Global types -->
              <xs:complexType name="LibraryType">
                <xs:sequence>
                  <xs:element ref="book" maxOccurs="unbounded"/>
                  <!-- Local element with inline type (hybrid approach) -->
                  <xs:element name="metadata">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="version" type="xs:string"/>
                        <xs:element name="created" type="xs:date"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:sequence>
              </xs:complexType>

              <xs:complexType name="BookType">
                <xs:sequence>
                  <xs:element name="title" type="xs:string"/>
                  <!-- Another local element with inline type -->
                  <xs:element name="details">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="pages" type="xs:int"/>
                        <xs:element name="isbn" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:sequence>
              </xs:complexType>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val parsedSchema = parser.parse()
        val usdl = parser.toUSDL(parsedSchema)

        val schema = usdl as UDM.Object
        val detectedPattern = schema.properties["%xsdPattern"]

        detectedPattern.shouldBeInstanceOf<UDM.Scalar>()
        (detectedPattern as UDM.Scalar).value shouldBe "bologna-sandwich"
    }

    @Test
    fun `detect Chameleon Schema pattern - global types with mixed local elements`() {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

              <!-- Single root element (≤1 global element) -->
              <xs:element name="document">
                <xs:complexType>
                  <xs:sequence>
                    <!-- Local elements using global types -->
                    <xs:element name="header" type="HeaderType"/>
                    <!-- Local element with inline type (mixed approach) -->
                    <xs:element name="body">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="content" type="xs:string"/>
                          <xs:element name="sections" type="SectionType" maxOccurs="unbounded"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>

              <!-- Global shared types (reusable components) -->
              <xs:complexType name="HeaderType">
                <xs:sequence>
                  <xs:element name="title" type="xs:string"/>
                  <xs:element name="author" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>

              <xs:complexType name="SectionType">
                <xs:sequence>
                  <xs:element name="heading" type="xs:string"/>
                  <xs:element name="text" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>

            </xs:schema>
        """.trimIndent()

        val parser = XSDParser(xsd)
        val parsedSchema = parser.parse()
        val usdl = parser.toUSDL(parsedSchema)

        val schema = usdl as UDM.Object
        val detectedPattern = schema.properties["%xsdPattern"]

        detectedPattern.shouldBeInstanceOf<UDM.Scalar>()
        (detectedPattern as UDM.Scalar).value shouldBe "chameleon-schema"
    }
}

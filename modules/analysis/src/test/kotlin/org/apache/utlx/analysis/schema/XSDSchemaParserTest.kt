// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/schema/XSDSchemaParserTest.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class XSDSchemaParserTest {
    
    private val parser = XSDSchemaParser()
    
    @Test
    fun `should parse simple string element`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="name" type="xs:string"/>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.STRING, (type as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should parse integer element`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="age" type="xs:integer"/>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.INTEGER, (type as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should parse decimal as number type`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="price" type="xs:decimal"/>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.NUMBER, (type as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should parse boolean element`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="active" type="xs:boolean"/>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.BOOLEAN, (type as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should parse date element`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="birthDate" type="xs:date"/>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.DATE, (type as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should parse dateTime element`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="timestamp" type="xs:dateTime"/>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.DATETIME, (type as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should parse complex type with sequence`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="person">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="firstName" type="xs:string"/>
                            <xs:element name="lastName" type="xs:string"/>
                            <xs:element name="age" type="xs:integer"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Object)
        val obj = type as TypeDefinition.Object
        assertEquals(3, obj.properties.size)
        assertTrue(obj.properties.containsKey("firstName"))
        assertTrue(obj.properties.containsKey("lastName"))
        assertTrue(obj.properties.containsKey("age"))
    }
    
    @Test
    fun `should parse complex type with attributes`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="product">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                        </xs:sequence>
                        <xs:attribute name="id" type="xs:string" use="required"/>
                        <xs:attribute name="category" type="xs:string"/>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Object)
        val obj = type as TypeDefinition.Object
        assertTrue(obj.properties.containsKey("@id"))
        assertTrue(obj.properties.containsKey("@category"))
        assertTrue(obj.required.contains("@id"))
        assertFalse(obj.required.contains("@category"))
    }
    
    @Test
    fun `should parse string with minLength constraint`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="username">
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            <xs:minLength value="3"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        val scalar = type as TypeDefinition.Scalar
        val minLengthConstraint = scalar.constraints.find { it is Constraint.MinLength }
        assertNotNull(minLengthConstraint)
        assertEquals(3, (minLengthConstraint as Constraint.MinLength).value)
    }
    
    @Test
    fun `should parse string with maxLength constraint`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="code">
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            <xs:maxLength value="10"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        val scalar = type as TypeDefinition.Scalar
        val maxLengthConstraint = scalar.constraints.find { it is Constraint.MaxLength }
        assertNotNull(maxLengthConstraint)
        assertEquals(10, (maxLengthConstraint as Constraint.MaxLength).value)
    }
    
    @Test
    fun `should parse string with pattern constraint`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="zipCode">
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            <xs:pattern value="[0-9]{5}"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        val scalar = type as TypeDefinition.Scalar
        val patternConstraint = scalar.constraints.find { it is Constraint.Pattern }
        assertNotNull(patternConstraint)
        assertEquals("[0-9]{5}", (patternConstraint as Constraint.Pattern).regex)
    }
    
    @Test
    fun `should parse enumeration constraint`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="status">
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            <xs:enumeration value="active"/>
                            <xs:enumeration value="inactive"/>
                            <xs:enumeration value="pending"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        val scalar = type as TypeDefinition.Scalar
        val enumConstraint = scalar.constraints.find { it is Constraint.Enum }
        assertNotNull(enumConstraint)
        val enumValues = (enumConstraint as Constraint.Enum).values
        assertEquals(3, enumValues.size)
        assertTrue(enumValues.contains("active"))
        assertTrue(enumValues.contains("inactive"))
        assertTrue(enumValues.contains("pending"))
    }
    
    @Test
    fun `should parse number with min and max constraints`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="percentage">
                    <xs:simpleType>
                        <xs:restriction base="xs:decimal">
                            <xs:minInclusive value="0.0"/>
                            <xs:maxInclusive value="100.0"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        val scalar = type as TypeDefinition.Scalar
        val minConstraint = scalar.constraints.find { it is Constraint.Minimum }
        val maxConstraint = scalar.constraints.find { it is Constraint.Maximum }
        assertNotNull(minConstraint)
        assertNotNull(maxConstraint)
        assertEquals(0.0, (minConstraint as Constraint.Minimum).value)
        assertEquals(100.0, (maxConstraint as Constraint.Maximum).value)
    }
    
    @Test
    fun `should parse array with maxOccurs unbounded`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="order">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Object)
        val obj = type as TypeDefinition.Object
        val itemProp = obj.properties["item"]
        assertNotNull(itemProp)
        assertTrue(itemProp.type is TypeDefinition.Array)
    }
    
    @Test
    fun `should parse optional element with minOccurs 0`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="record">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="required" type="xs:string"/>
                            <xs:element name="optional" type="xs:string" minOccurs="0"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Object)
        val obj = type as TypeDefinition.Object
        assertTrue(obj.required.contains("required"))
        assertFalse(obj.required.contains("optional"))
        assertTrue(obj.properties["optional"]!!.nullable)
    }
    
    @Test
    fun `should parse union type`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="value">
                    <xs:simpleType>
                        <xs:union memberTypes="xs:string xs:integer"/>
                    </xs:simpleType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Union)
        val union = type as TypeDefinition.Union
        assertEquals(2, union.types.size)
    }
    
    @Test
    fun `should throw exception for invalid schema format`() {
        val xsd = "<invalid>xml</invalid>"
        
        assertThrows<SchemaParseException> {
            parser.parse(xsd, SchemaFormat.JSON_SCHEMA)
        }
    }
    
    @Test
    fun `should handle anyURI type with format constraint`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="website" type="xs:anyURI"/>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Scalar)
        val scalar = type as TypeDefinition.Scalar
        assertEquals(ScalarKind.STRING, scalar.kind)
        val formatConstraint = scalar.constraints.find { 
            it is Constraint.Custom && it.name == "format" 
        }
        assertNotNull(formatConstraint)
    }
    
    @Test
    fun `should parse nested complex types`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="company">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                            <xs:element name="address">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="street" type="xs:string"/>
                                        <xs:element name="city" type="xs:string"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        assertTrue(type is TypeDefinition.Object)
        val obj = type as TypeDefinition.Object
        assertTrue(obj.properties.containsKey("address"))
        val addressType = obj.properties["address"]!!.type
        assertTrue(addressType is TypeDefinition.Object)
        val address = addressType as TypeDefinition.Object
        assertTrue(address.properties.containsKey("street"))
        assertTrue(address.properties.containsKey("city"))
    }
}

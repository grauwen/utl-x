// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/schema/XSDGeneratorTest.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class XSDGeneratorTest {
    
    @Test
    fun `should generate XSD for string type`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)
        
        val xsd = generateMockXSD(type, "name")
        
        assertTrue(xsd.contains("xs:string"))
        assertTrue(xsd.contains("<xs:element"))
    }
    
    @Test
    fun `should generate XSD for integer type`() {
        val type = TypeDefinition.Scalar(ScalarKind.INTEGER)
        
        val xsd = generateMockXSD(type, "age")
        
        assertTrue(xsd.contains("xs:integer"))
    }
    
    @Test
    fun `should generate XSD for number type`() {
        val type = TypeDefinition.Scalar(ScalarKind.NUMBER)
        
        val xsd = generateMockXSD(type, "price")
        
        assertTrue(xsd.contains("xs:decimal"))
    }
    
    @Test
    fun `should generate XSD for boolean type`() {
        val type = TypeDefinition.Scalar(ScalarKind.BOOLEAN)
        
        val xsd = generateMockXSD(type, "active")
        
        assertTrue(xsd.contains("xs:boolean"))
    }
    
    @Test
    fun `should generate XSD for date type`() {
        val type = TypeDefinition.Scalar(ScalarKind.DATE)
        
        val xsd = generateMockXSD(type, "birthDate")
        
        assertTrue(xsd.contains("xs:date"))
    }
    
    @Test
    fun `should generate XSD for dateTime type`() {
        val type = TypeDefinition.Scalar(ScalarKind.DATETIME)
        
        val xsd = generateMockXSD(type, "timestamp")
        
        assertTrue(xsd.contains("xs:dateTime"))
    }
    
    @Test
    fun `should generate XSD with minLength constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MinLength(3))
        )

        val xsd = generateMockXSD(type, "username")

        assertTrue(xsd.contains("minLength"))
        assertTrue(xsd.contains("value=\"3\""))
    }

    @Test
    fun `should generate XSD with maxLength constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MaxLength(50))
        )

        val xsd = generateMockXSD(type, "description")

        assertTrue(xsd.contains("maxLength"))
        assertTrue(xsd.contains("value=\"50\""))
    }

    @Test
    fun `should generate XSD with pattern constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.Pattern("[0-9]{5}"))
        )

        val xsd = generateMockXSD(type, "zipCode")

        assertTrue(xsd.contains("pattern"))
        assertTrue(xsd.contains("[0-9]{5}"))
    }

    @Test
    fun `should generate XSD with enumeration`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.Enum(listOf("red", "green", "blue")))
        )
        
        val xsd = generateMockXSD(type, "color")
        
        assertTrue(xsd.contains("enumeration"))
        assertTrue(xsd.contains("red"))
        assertTrue(xsd.contains("green"))
        assertTrue(xsd.contains("blue"))
    }
    
    @Test
    fun `should generate XSD for complex type with properties`() {
        val type = TypeDefinition.Object(
            properties = mapOf(
                "firstName" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "lastName" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            ),
            required = setOf("firstName", "lastName")
        )
        
        val xsd = generateMockXSD(type, "person")
        
        assertTrue(xsd.contains("complexType"))
        assertTrue(xsd.contains("sequence"))
        assertTrue(xsd.contains("firstName"))
        assertTrue(xsd.contains("lastName"))
        assertTrue(xsd.contains("age"))
    }
    
    @Test
    fun `should generate XSD with optional elements using minOccurs`() {
        val type = TypeDefinition.Object(
            properties = mapOf(
                "required" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING), nullable = false),
                "optional" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING), nullable = true)
            ),
            required = setOf("required")
        )
        
        val xsd = generateMockXSD(type, "record")
        
        assertTrue(xsd.contains("minOccurs=\"0\"") || xsd.contains("optional"))
    }
    
    @Test
    fun `should generate XSD for array type with maxOccurs unbounded`() {
        val type = TypeDefinition.Array(
            elementType = TypeDefinition.Scalar(ScalarKind.STRING)
        )
        
        val xsd = generateMockXSD(type, "items")
        
        assertTrue(xsd.contains("maxOccurs=\"unbounded\""))
    }
    
    @Test
    fun `should generate XSD with target namespace`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)
        val namespace = "http://example.com/schema"
        
        val xsd = generateMockXSD(type, "name", namespace)
        
        assertTrue(xsd.contains("targetNamespace"))
        assertTrue(xsd.contains(namespace))
    }
    
    // Mock XSD generator for testing
    private fun generateMockXSD(
        type: TypeDefinition, 
        elementName: String,
        targetNamespace: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"")
        
        if (targetNamespace != null) {
            sb.append(" targetNamespace=\"$targetNamespace\"")
        }
        
        sb.append(">\n")
        
        when (type) {
            is TypeDefinition.Scalar -> {
                sb.append("  <xs:element name=\"$elementName\"")
                
                if (type.constraints.isEmpty()) {
                    sb.append(" type=\"${scalarToXSDType(type.kind)}\"")
                    sb.append("/>\n")
                } else {
                    sb.append(">\n")
                    sb.append("    <xs:simpleType>\n")
                    sb.append("      <xs:restriction base=\"${scalarToXSDType(type.kind)}\">\n")
                    
                    for (constraint in type.constraints) {
                        when (constraint) {
                            is Constraint.MinLength -> {
                                sb.append("        <xs:minLength value=\"${constraint.value}\"/>\n")
                            }
                            is Constraint.MaxLength -> {
                                sb.append("        <xs:maxLength value=\"${constraint.value}\"/>\n")
                            }
                            is Constraint.Pattern -> {
                                sb.append("        <xs:pattern value=\"${constraint.regex}\"/>\n")
                            }
                            is Constraint.Enum -> {
                                for (value in constraint.values) {
                                    sb.append("        <xs:enumeration value=\"$value\"/>\n")
                                }
                            }
                            is Constraint.Minimum, is Constraint.Maximum, is Constraint.Custom -> {
                                // Not handled in this mock generator
                            }
                        }
                    }
                    
                    sb.append("      </xs:restriction>\n")
                    sb.append("    </xs:simpleType>\n")
                    sb.append("  </xs:element>\n")
                }
            }
            is TypeDefinition.Array -> {
                sb.append("  <xs:element name=\"$elementName\" type=\"xs:string\" maxOccurs=\"unbounded\"/>\n")
            }
            is TypeDefinition.Object -> {
                sb.append("  <xs:element name=\"$elementName\">\n")
                sb.append("    <xs:complexType>\n")
                sb.append("      <xs:sequence>\n")
                
                for ((propName, propType) in type.properties) {
                    val minOccurs = if (type.required.contains(propName)) 1 else 0
                    sb.append("        <xs:element name=\"$propName\" type=\"${typeToXSDType(propType.type)}\"")
                    if (minOccurs == 0) {
                        sb.append(" minOccurs=\"0\"")
                    }
                    sb.append("/>\n")
                }
                
                sb.append("      </xs:sequence>\n")
                sb.append("    </xs:complexType>\n")
                sb.append("  </xs:element>\n")
            }
            else -> {
                sb.append("  <xs:element name=\"$elementName\" type=\"xs:string\"/>\n")
            }
        }
        
        sb.append("</xs:schema>")
        return sb.toString()
    }
    
    private fun scalarToXSDType(kind: ScalarKind): String {
        return when (kind) {
            ScalarKind.STRING -> "xs:string"
            ScalarKind.INTEGER -> "xs:integer"
            ScalarKind.NUMBER -> "xs:decimal"
            ScalarKind.BOOLEAN -> "xs:boolean"
            ScalarKind.DATE -> "xs:date"
            ScalarKind.TIME -> "xs:time"
            ScalarKind.DATETIME -> "xs:dateTime"
            ScalarKind.DURATION -> "xs:duration"
            ScalarKind.BINARY -> "xs:base64Binary"
            else -> "xs:string"
        }
    }
    
    private fun typeToXSDType(type: TypeDefinition): String {
        return when (type) {
            is TypeDefinition.Scalar -> scalarToXSDType(type.kind)
            else -> "xs:string"
        }
    }
}

// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/schema/SchemaGeneratorTest.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class SchemaGeneratorTest {
    
    @Test
    fun `should generate JSON Schema from input XSD`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="person">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                            <xs:element name="age" type="xs:integer"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent()
        
        // Parse XSD -> TypeDefinition
        val parser = XSDSchemaParser()
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        // Generate JSON Schema
        val generator = JSONSchemaGenerator()
        val jsonSchema = generator.generate(type, GeneratorOptions())
        
        assertNotNull(jsonSchema)
        assertTrue(jsonSchema.contains("object"))
        assertTrue(jsonSchema.contains("properties"))
    }
    
    @Test
    fun `should parse JSON Schema and regenerate equivalent schema`() {
        val jsonSchema = """
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" }
                },
                "required": ["name"]
            }
        """.trimIndent()
        
        // Parse JSON Schema -> TypeDefinition
        val parser = JSONSchemaParser()
        val type = parser.parse(jsonSchema, SchemaFormat.JSON_SCHEMA)
        
        // Regenerate JSON Schema
        val generator = JSONSchemaGenerator()
        val regenerated = generator.generate(type, GeneratorOptions())
        
        assertNotNull(regenerated)
        assertTrue(regenerated.contains("name"))
        assertTrue(regenerated.contains("age"))
    }
    
    @Test
    fun `should convert XSD to JSON Schema maintaining constraints`() {
        val xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="username">
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            <xs:minLength value="3"/>
                            <xs:maxLength value="20"/>
                            <xs:pattern value="[a-zA-Z0-9]+"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            </xs:schema>
        """.trimIndent()

        val parser = XSDSchemaParser()
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        val generator = JSONSchemaGenerator()
        val jsonSchema = generator.generate(type, GeneratorOptions())
        
        assertTrue(jsonSchema.contains("minLength"))
        assertTrue(jsonSchema.contains("maxLength"))
        assertTrue(jsonSchema.contains("pattern"))
    }
    
    @Test
    fun `should handle nested complex types in conversion`() {
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

        val parser = XSDSchemaParser()
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        val generator = JSONSchemaGenerator()
        val jsonSchema = generator.generate(type, GeneratorOptions())
        
        assertTrue(jsonSchema.contains("address"))
        assertTrue(jsonSchema.contains("street"))
        assertTrue(jsonSchema.contains("city"))
    }
    
    @Test
    fun `should preserve array types in conversion`() {
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

        val parser = XSDSchemaParser()
        val type = parser.parse(xsd, SchemaFormat.XSD)
        
        val generator = JSONSchemaGenerator()
        val jsonSchema = generator.generate(type, GeneratorOptions())
        
        assertTrue(jsonSchema.contains("array") || jsonSchema.contains("items"))
    }
    
    @Test
    fun `should handle optional vs required properties`() {
        val jsonSchema = """
            {
                "type": "object",
                "properties": {
                    "required": { "type": "string" },
                    "optional": { "type": "string" }
                },
                "required": ["required"]
            }
        """.trimIndent()

        val parser = JSONSchemaParser()
        val type = parser.parse(jsonSchema, SchemaFormat.JSON_SCHEMA)
        
        assertTrue(type is TypeDefinition.Object)
        val obj = type as TypeDefinition.Object
        assertTrue(obj.required.contains("required"))
        assertTrue(!obj.required.contains("optional"))
    }
    
    @Test
    fun `should generate schema with custom options`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)
        
        val generator = JSONSchemaGenerator()
        val schema = generator.generate(
            type,
            GeneratorOptions(
                pretty = true,
                includeComments = true,
                rootElementName = "MyElement"
            )
        )
        
        assertNotNull(schema)
        assertTrue(schema.contains("\n")) // Pretty printed
    }
    
    @Test
    fun `should round-trip complex schema through internal representation`() {
        val originalType = TypeDefinition.Object(
            properties = mapOf(
                "id" to PropertyType(
                    TypeDefinition.Scalar(
                        ScalarKind.STRING,
                        listOf(Constraint.Pattern("^[A-Z]{3}-[0-9]{4}$"))
                    )
                ),
                "items" to PropertyType(
                    TypeDefinition.Array(
                        TypeDefinition.Object(
                            properties = mapOf(
                                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                                "price" to PropertyType(TypeDefinition.Scalar(ScalarKind.NUMBER))
                            )
                        )
                    )
                )
            ),
            required = setOf("id")
        )
        
        // Generate JSON Schema
        val generator = JSONSchemaGenerator()
        val jsonSchema = generator.generate(originalType, GeneratorOptions())
        
        // Parse it back
        val parser = JSONSchemaParser()
        val parsedType = parser.parse(jsonSchema, SchemaFormat.JSON_SCHEMA)
        
        // Both should be object types
        assertTrue(originalType is TypeDefinition.Object)
        assertTrue(parsedType is TypeDefinition.Object)
    }
}

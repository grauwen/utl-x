// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/schema/JSONSchemaGeneratorTest.kt
package org.apache.utlx.analysis.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.utlx.analysis.types.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class JSONSchemaGeneratorTest {
    
    private val generator = JSONSchemaGenerator()
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `should generate schema for string type`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("string", parsed["type"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `should generate schema for integer type`() {
        val type = TypeDefinition.Scalar(ScalarKind.INTEGER)

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("integer", parsed["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should generate schema for number type`() {
        val type = TypeDefinition.Scalar(ScalarKind.NUMBER)

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("number", parsed["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should generate schema for boolean type`() {
        val type = TypeDefinition.Scalar(ScalarKind.BOOLEAN)

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("boolean", parsed["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should generate schema for date type with format`() {
        val type = TypeDefinition.Scalar(ScalarKind.DATE)

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("string", parsed["type"]?.jsonPrimitive?.content)
        assertEquals("date", parsed["format"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should generate schema for dateTime type with format`() {
        val type = TypeDefinition.Scalar(ScalarKind.DATETIME)

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("string", parsed["type"]?.jsonPrimitive?.content)
        assertEquals("date-time", parsed["format"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should generate schema with minLength constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MinLength(3))
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals(3, parsed["minLength"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `should generate schema with maxLength constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MaxLength(50))
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals(50, parsed["maxLength"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `should generate schema with pattern constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.Pattern("^[A-Z]{3}$"))
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("^[A-Z]{3}$", parsed["pattern"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should generate schema with enum constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.Enum(listOf("red", "green", "blue")))
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertNotNull(parsed["enum"])
    }

    @Test
    fun `should generate schema with minimum constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.NUMBER,
            listOf(Constraint.Minimum(0.0))
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals(0.0, parsed["minimum"]?.jsonPrimitive?.content?.toDouble())
    }

    @Test
    fun `should generate schema with maximum constraint`() {
        val type = TypeDefinition.Scalar(
            ScalarKind.NUMBER,
            listOf(Constraint.Maximum(100.0))
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals(100.0, parsed["maximum"]?.jsonPrimitive?.content?.toDouble())
    }

    @Test
    fun `should generate schema for array type`() {
        val type = TypeDefinition.Array(
            elementType = TypeDefinition.Scalar(ScalarKind.STRING)
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("array", parsed["type"]?.jsonPrimitive?.content)
        assertNotNull(parsed["items"])
    }

    @Test
    fun `should generate schema for array with minItems and maxItems`() {
        val type = TypeDefinition.Array(
            elementType = TypeDefinition.Scalar(ScalarKind.INTEGER),
            minItems = 1,
            maxItems = 10
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals(1, parsed["minItems"]?.jsonPrimitive?.content?.toInt())
        assertEquals(10, parsed["maxItems"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `should generate schema for object type`() {
        val type = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(
                    type = TypeDefinition.Scalar(ScalarKind.STRING),
                    nullable = false
                ),
                "age" to PropertyType(
                    type = TypeDefinition.Scalar(ScalarKind.INTEGER),
                    nullable = false
                )
            ),
            required = setOf("name", "age")
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertEquals("object", parsed["type"]?.jsonPrimitive?.content)
        assertNotNull(parsed["properties"])
        assertNotNull(parsed["required"])
    }

    @Test
    fun `should generate schema for object with optional properties`() {
        val type = TypeDefinition.Object(
            properties = mapOf(
                "required" to PropertyType(
                    type = TypeDefinition.Scalar(ScalarKind.STRING),
                    nullable = false
                ),
                "optional" to PropertyType(
                    type = TypeDefinition.Scalar(ScalarKind.STRING),
                    nullable = true
                )
            ),
            required = setOf("required")
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertNotNull(parsed["required"])
    }

    @Test
    fun `should generate schema for union type using anyOf`() {
        val type = TypeDefinition.Union(
            listOf(
                TypeDefinition.Scalar(ScalarKind.STRING),
                TypeDefinition.Scalar(ScalarKind.INTEGER)
            )
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertNotNull(parsed["anyOf"])
    }

    @Test
    fun `should generate schema for nested object`() {
        val type = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(
                    type = TypeDefinition.Scalar(ScalarKind.STRING),
                    nullable = false
                ),
                "address" to PropertyType(
                    type = TypeDefinition.Object(
                        properties = mapOf(
                            "street" to PropertyType(
                                type = TypeDefinition.Scalar(ScalarKind.STRING),
                                nullable = false
                            ),
                            "city" to PropertyType(
                                type = TypeDefinition.Scalar(ScalarKind.STRING),
                                nullable = false
                            )
                        ),
                        required = setOf("street", "city")
                    ),
                    nullable = false
                )
            ),
            required = setOf("name", "address")
        )

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertNotNull(parsed["properties"])
        val properties = parsed["properties"]!!.jsonObject
        assertNotNull(properties["address"])
    }

    @Test
    fun `should include schema version in output`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)

        val schema = generator.generate(type, GeneratorOptions())
        val parsed = json.parseToJsonElement(schema).jsonObject

        assertTrue(parsed.containsKey("\$schema"))
    }
    
    @Test
    fun `should generate pretty printed schema when option enabled`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)

        val schema = generator.generate(
            type,
            GeneratorOptions(pretty = true)
        )

        // Pretty printed JSON should contain newlines
        assertTrue(schema.contains("\n"))
    }

    @Test
    fun `should generate schema without errors`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)

        val schema = generator.generate(
            type,
            GeneratorOptions(includeComments = true)
        )
        val parsed = json.parseToJsonElement(schema).jsonObject

        // This test verifies the schema is generated without errors
        assertNotNull(schema)
        assertTrue(parsed.containsKey("type"))
    }
}

package org.apache.utlx.formats.jsch

import org.apache.utlx.core.udm.UDM
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class JSONSchemaParserTest {

    @Test
    fun `parse basic JSON Schema draft-07`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                },
                "age": {
                  "type": "integer"
                }
              },
              "required": ["name"]
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        // Verify it's an object
        udm.shouldBeInstanceOf<UDM.Object>()
        val schema = udm as UDM.Object

        // Verify schema metadata
        schema.metadata["__schemaType"] shouldBe "jsch-schema"
        schema.metadata["__version"] shouldBe "draft-07"
    }

    @Test
    fun `parse JSON Schema 2020-12`() {
        val jsonSchema = """
            {
              "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "${'$'}defs": {
                "person": {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"}
                  }
                }
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // Verify version detection
        schema.metadata["__version"] shouldBe "2020-12"
    }

    @Test
    fun `parse JSON Schema draft-04`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-04/schema#",
              "type": "object",
              "properties": {
                "id": {"type": "string"}
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // Verify version detection
        schema.metadata["__version"] shouldBe "draft-04"
    }

    @Test
    fun `default to draft-07 when no ${'$'}schema field`() {
        val jsonSchema = """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"}
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // Should return "undefined" when no $schema field present
        schema.metadata["__version"] shouldBe "undefined"
    }

    @Test
    fun `tag properties with jsch-properties metadata`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "username": {
                  "type": "string",
                  "minLength": 3
                },
                "email": {
                  "type": "string",
                  "format": "email"
                }
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // Verify properties are tagged
        val properties = schema.properties["properties"] as? UDM.Object
        properties shouldNotBe null
        if (properties != null) {
            properties.metadata["__schemaType"] shouldBe "jsch-properties"

            // Individual properties should be tagged
            val username = properties.properties["username"] as? UDM.Object
            if (username != null) {
                username.metadata["__schemaType"] shouldBe "jsch-property"
            }
        }
    }

    @Test
    fun `tag definitions with jsch-definitions metadata`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "definitions": {
                "address": {
                  "type": "object",
                  "properties": {
                    "street": {"type": "string"},
                    "city": {"type": "string"}
                  }
                },
                "person": {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"}
                  }
                }
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // Verify definitions are tagged
        val definitions = schema.properties["definitions"] as? UDM.Object
        definitions shouldNotBe null
        if (definitions != null) {
            definitions.metadata["__schemaType"] shouldBe "jsch-definitions"

            // Individual definitions should be tagged
            val address = definitions.properties["address"] as? UDM.Object
            if (address != null) {
                address.metadata["__schemaType"] shouldBe "jsch-definition"
            }
        }
    }

    @Test
    fun `parse schema with ${'$'}ref references`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "definitions": {
                "name": {
                  "type": "string"
                }
              },
              "type": "object",
              "properties": {
                "firstName": {
                  "${'$'}ref": "#/definitions/name"
                },
                "lastName": {
                  "${'$'}ref": "#/definitions/name"
                }
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        // Should parse without errors
        udm.shouldBeInstanceOf<UDM.Object>()
        val schema = udm as UDM.Object
        schema.metadata["__schemaType"] shouldBe "jsch-schema"
    }

    @Test
    fun `parse schema with nested objects`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "user": {
                  "type": "object",
                  "properties": {
                    "profile": {
                      "type": "object",
                      "properties": {
                        "bio": {"type": "string"}
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        udm.shouldBeInstanceOf<UDM.Object>()
        val schema = udm as UDM.Object
        schema.metadata["__schemaType"] shouldBe "jsch-schema"
    }

    @Test
    fun `parse schema with array items`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "type": "array",
              "items": {
                "type": "string"
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // Verify items are tagged
        val items = schema.properties["items"] as? UDM.Object
        if (items != null) {
            items.metadata["__schemaType"] shouldBe "jsch-items"
        }
    }

    @Test
    fun `parse schema with validation keywords`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "email": {
                  "type": "string",
                  "format": "email",
                  "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}${'$'}",
                  "minLength": 5,
                  "maxLength": 100
                },
                "age": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 150
                }
              },
              "required": ["email"]
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        udm.shouldBeInstanceOf<UDM.Object>()
        val schema = udm as UDM.Object
        schema.metadata["__schemaType"] shouldBe "jsch-schema"

        // Verify required field exists
        schema.properties["required"] shouldNotBe null
    }

    @Test
    fun `parse schema with examples and description`() {
        val jsonSchema = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "title": "User Schema",
              "description": "Schema for user objects",
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "User's full name",
                  "examples": ["John Doe", "Jane Smith"]
                }
              }
            }
        """.trimIndent()

        val parser = JSONSchemaParser(jsonSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object

        // Verify title and description are preserved
        val title = schema.properties["title"] as? UDM.Scalar
        title?.value shouldBe "User Schema"

        val description = schema.properties["description"] as? UDM.Scalar
        description?.value shouldBe "Schema for user objects"
    }

    // ── F08: USDL Enrichment Tests ──

    @Test
    fun `F08 - parse produces both raw JSON Schema and USDL properties`() {
        val jsch = """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "age": {"type": "integer"}
              },
              "required": ["name"]
            }
        """.trimIndent()

        val udm = JSONSchemaParser(jsch).parse()
        udm.shouldBeInstanceOf<UDM.Object>()
        val schema = udm as UDM.Object

        // Raw JSON Schema access still works
        (schema.properties["type"] as? UDM.Scalar)?.value shouldBe "object"
        schema.properties.containsKey("properties") shouldBe true

        // USDL % properties are present
        schema.properties.containsKey("%types") shouldBe true
        schema.properties.containsKey("%_diagnostics") shouldBe true

        // Diagnostics show complete
        val diagnostics = schema.properties["%_diagnostics"] as UDM.Object
        (diagnostics.properties["%_status"] as UDM.Scalar).value shouldBe "complete"
    }

    @Test
    fun `F08 - USDL types contain Root type from JSON Schema`() {
        val jsch = """
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"}
              }
            }
        """.trimIndent()

        val udm = JSONSchemaParser(jsch).parse() as UDM.Object
        val types = udm.properties["%types"] as UDM.Object

        types.properties.containsKey("Root") shouldBe true
    }

    // ── F10: Decimal Format Detection ──

    @Test
    fun `F10 - format decimal detected as USDL decimal type`() {
        val jsch = """
            {
              "type": "object",
              "properties": {
                "amount": {"type": "string", "format": "decimal"},
                "name": {"type": "string"},
                "count": {"type": "integer"}
              }
            }
        """.trimIndent()

        val udm = JSONSchemaParser(jsch).parse() as UDM.Object
        val types = udm.properties["%types"] as UDM.Object
        val fields = (types.properties["Root"] as UDM.Object).properties["%fields"] as UDM.Array

        // "type": "string", "format": "decimal" → USDL "decimal"
        val amountField = fields.elements[0] as UDM.Object
        (amountField.properties["%type"] as UDM.Scalar).value shouldBe "decimal"

        // "type": "string" (no format) → USDL "string"
        val nameField = fields.elements[1] as UDM.Object
        (nameField.properties["%type"] as UDM.Scalar).value shouldBe "string"

        // "type": "integer" → USDL "integer"
        val countField = fields.elements[2] as UDM.Object
        (countField.properties["%type"] as UDM.Scalar).value shouldBe "integer"
    }
}

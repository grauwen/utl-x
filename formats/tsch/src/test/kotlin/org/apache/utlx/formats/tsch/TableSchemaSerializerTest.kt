package org.apache.utlx.formats.tsch

import org.apache.utlx.core.udm.UDM
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class TableSchemaSerializerTest {

    @Test
    fun `serialize LOW_LEVEL mode - pass through Table Schema structure`() {
        // Build a Table Schema UDM directly
        val udm = UDM.Object(properties = mapOf(
            "fields" to UDM.Array(listOf(
                UDM.Object(properties = mapOf(
                    "name" to UDM.Scalar("id"),
                    "type" to UDM.Scalar("integer"),
                    "constraints" to UDM.Object(properties = mapOf(
                        "required" to UDM.Scalar(true)
                    ))
                )),
                UDM.Object(properties = mapOf(
                    "name" to UDM.Scalar("name"),
                    "type" to UDM.Scalar("string")
                ))
            )),
            "primaryKey" to UDM.Scalar("id")
        ))

        val serializer = TableSchemaSerializer(prettyPrint = true)
        val output = serializer.serialize(udm)

        output shouldContain "\"fields\""
        output shouldContain "\"id\""
        output shouldContain "\"integer\""
        output shouldContain "\"primaryKey\""
    }

    @Test
    fun `serialize UNIVERSAL_DSL mode - USDL to Table Schema`() {
        // Build USDL structure
        val udm = UDM.Object(properties = mapOf(
            "%types" to UDM.Object(properties = mapOf(
                "Employee" to UDM.Object(properties = mapOf(
                    "%kind" to UDM.Scalar("structure"),
                    "%fields" to UDM.Array(listOf(
                        UDM.Object(properties = mapOf(
                            "%name" to UDM.Scalar("id"),
                            "%type" to UDM.Scalar("integer"),
                            "%required" to UDM.Scalar(true),
                            "%description" to UDM.Scalar("Employee ID")
                        )),
                        UDM.Object(properties = mapOf(
                            "%name" to UDM.Scalar("name"),
                            "%type" to UDM.Scalar("string"),
                            "%description" to UDM.Scalar("Employee name")
                        ))
                    )),
                    "%key" to UDM.Scalar("id")
                ))
            ))
        ))

        val serializer = TableSchemaSerializer(prettyPrint = true)
        val output = serializer.serialize(udm)

        output shouldContain "\"fields\""
        output shouldContain "\"id\""
        output shouldContain "\"integer\""
        output shouldContain "\"Employee ID\""
        output shouldContain "\"primaryKey\""
    }

    @Test
    fun `round-trip - parse then serialize`() {
        val tableSchema = """
            {
              "fields": [
                {
                  "name": "id",
                  "type": "integer",
                  "description": "Record identifier",
                  "constraints": {
                    "required": true
                  }
                },
                {
                  "name": "name",
                  "type": "string",
                  "description": "Full name"
                }
              ],
              "primaryKey": "id"
            }
        """.trimIndent()

        // Parse
        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()

        // Serialize back
        val serializer = TableSchemaSerializer(prettyPrint = true)
        val output = serializer.serialize(udm)

        // Verify structure is preserved
        output shouldContain "\"fields\""
        output shouldContain "\"id\""
        output shouldContain "\"integer\""
        output shouldContain "\"primaryKey\""
        output shouldContain "\"Record identifier\""
    }

    @Test
    fun `round-trip via USDL - parse to USDL then serialize back`() {
        val tableSchema = """
            {
              "fields": [
                {
                  "name": "id",
                  "type": "integer",
                  "constraints": {"required": true}
                },
                {
                  "name": "email",
                  "type": "string"
                }
              ],
              "primaryKey": "id"
            }
        """.trimIndent()

        // Parse
        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()

        // Convert to USDL
        val usdl = parser.toUSDL(udm)

        // Serialize USDL back to Table Schema
        val serializer = TableSchemaSerializer(prettyPrint = true)
        val output = serializer.serialize(usdl)

        // Verify key structural elements
        output shouldContain "\"fields\""
        output shouldContain "\"id\""
        output shouldContain "\"integer\""
        output shouldContain "\"email\""
        output shouldContain "\"string\""
        output shouldContain "\"primaryKey\""
    }

    @Test
    fun `serialize with enum constraints`() {
        val udm = UDM.Object(properties = mapOf(
            "%types" to UDM.Object(properties = mapOf(
                "Order" to UDM.Object(properties = mapOf(
                    "%kind" to UDM.Scalar("structure"),
                    "%fields" to UDM.Array(listOf(
                        UDM.Object(properties = mapOf(
                            "%name" to UDM.Scalar("status"),
                            "%type" to UDM.Scalar("string"),
                            "%required" to UDM.Scalar(true),
                            "%enum" to UDM.Array(listOf(
                                UDM.Scalar("pending"),
                                UDM.Scalar("confirmed"),
                                UDM.Scalar("shipped")
                            ))
                        ))
                    ))
                ))
            ))
        ))

        val serializer = TableSchemaSerializer(prettyPrint = true)
        val output = serializer.serialize(udm)

        output shouldContain "\"enum\""
        output shouldContain "\"pending\""
        output shouldContain "\"confirmed\""
        output shouldContain "\"shipped\""
    }
}

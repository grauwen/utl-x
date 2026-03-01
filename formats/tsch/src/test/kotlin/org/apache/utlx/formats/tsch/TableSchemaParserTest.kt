package org.apache.utlx.formats.tsch

import org.apache.utlx.core.udm.UDM
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.io.File

class TableSchemaParserTest {

    @Test
    fun `parse basic Table Schema with fields and types`() {
        val tableSchema = """
            {
              "fields": [
                {"name": "id", "type": "integer"},
                {"name": "name", "type": "string"},
                {"name": "active", "type": "boolean"}
              ]
            }
        """.trimIndent()

        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()

        udm.shouldBeInstanceOf<UDM.Object>()
        val schema = udm as UDM.Object

        // Verify schema metadata
        schema.metadata["__schemaType"] shouldBe "tsch-schema"

        // Verify fields exist
        val fields = schema.properties["fields"] as? UDM.Array
        fields shouldNotBe null
        fields!!.elements.size shouldBe 3
    }

    @Test
    fun `parse Table Schema with constraints`() {
        val tableSchema = """
            {
              "fields": [
                {
                  "name": "employee_id",
                  "type": "string",
                  "constraints": {
                    "required": true,
                    "pattern": "^EMP-[0-9]{3}$"
                  }
                },
                {
                  "name": "salary",
                  "type": "number",
                  "constraints": {
                    "required": true,
                    "minimum": 30000
                  }
                }
              ]
            }
        """.trimIndent()

        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object
        schema.metadata["__schemaType"] shouldBe "tsch-schema"

        val fields = schema.properties["fields"] as UDM.Array
        fields.elements.size shouldBe 2

        // Verify fields are tagged
        val firstField = fields.elements[0] as UDM.Object
        firstField.metadata["__schemaType"] shouldBe "tsch-field"
    }

    @Test
    fun `parse Table Schema with composite primaryKey`() {
        val tableSchema = """
            {
              "fields": [
                {"name": "invoice_number", "type": "string"},
                {"name": "line_number", "type": "integer"},
                {"name": "amount", "type": "number"}
              ],
              "primaryKey": ["invoice_number", "line_number"]
            }
        """.trimIndent()

        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object
        schema.metadata["__schemaType"] shouldBe "tsch-schema"

        // Verify composite primaryKey
        val pk = schema.properties["primaryKey"] as? UDM.Array
        pk shouldNotBe null
        pk!!.elements.size shouldBe 2
    }

    @Test
    fun `parse Table Schema with foreignKeys`() {
        val tableSchema = """
            {
              "fields": [
                {"name": "id", "type": "string"},
                {"name": "manager_id", "type": "string"}
              ],
              "primaryKey": "id",
              "foreignKeys": [
                {
                  "fields": ["manager_id"],
                  "reference": {
                    "resource": "",
                    "fields": ["id"]
                  }
                }
              ]
            }
        """.trimIndent()

        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object
        schema.metadata["__schemaType"] shouldBe "tsch-schema"

        // Verify foreignKeys exist
        val fks = schema.properties["foreignKeys"] as? UDM.Array
        fks shouldNotBe null
        fks!!.elements.size shouldBe 1
    }

    @Test
    fun `parse Table Schema with missingValues`() {
        val tableSchema = """
            {
              "fields": [
                {"name": "value", "type": "number"}
              ],
              "missingValues": ["", "NA", "N/A"]
            }
        """.trimIndent()

        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object
        schema.metadata["__schemaType"] shouldBe "tsch-schema"

        val mv = schema.properties["missingValues"] as? UDM.Array
        mv shouldNotBe null
        mv!!.elements.size shouldBe 3
    }

    @Test
    fun `parse Table Schema with all field descriptor properties`() {
        val tableSchema = """
            {
              "fields": [
                {
                  "name": "price",
                  "type": "number",
                  "title": "Product Price",
                  "description": "Price in EUR",
                  "decimalChar": ",",
                  "groupChar": ".",
                  "bareNumber": false,
                  "constraints": {
                    "required": true,
                    "minimum": 0.01
                  }
                },
                {
                  "name": "active",
                  "type": "boolean",
                  "trueValues": ["yes", "1", "true"],
                  "falseValues": ["no", "0", "false"]
                }
              ]
            }
        """.trimIndent()

        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()

        val schema = udm as UDM.Object
        schema.metadata["__schemaType"] shouldBe "tsch-schema"

        val fields = schema.properties["fields"] as UDM.Array
        fields.elements.size shouldBe 2

        // Verify first field has decimalChar
        val priceField = fields.elements[0] as UDM.Object
        val decimalChar = priceField.properties["decimalChar"] as? UDM.Scalar
        decimalChar?.value shouldBe ","
    }

    @Test
    fun `toUSDL conversion`() {
        val tableSchema = """
            {
              "fields": [
                {
                  "name": "id",
                  "type": "integer",
                  "description": "Record identifier",
                  "constraints": {"required": true}
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

        val parser = TableSchemaParser(tableSchema)
        val udm = parser.parse()
        val usdl = parser.toUSDL(udm)

        usdl.shouldBeInstanceOf<UDM.Object>()
        val usdlObj = usdl as UDM.Object

        // Verify %types exists
        val types = usdlObj.properties["%types"] as? UDM.Object
        types shouldNotBe null

        // Verify Record type
        val record = types!!.properties["Record"] as? UDM.Object
        record shouldNotBe null

        val kind = (record!!.properties["%kind"] as? UDM.Scalar)?.value
        kind shouldBe "structure"

        // Verify fields
        val fields = record.properties["%fields"] as? UDM.Array
        fields shouldNotBe null
        fields!!.elements.size shouldBe 2

        // Verify first field
        val firstField = fields.elements[0] as UDM.Object
        (firstField.properties["%name"] as? UDM.Scalar)?.value shouldBe "id"
        (firstField.properties["%type"] as? UDM.Scalar)?.value shouldBe "integer"
        (firstField.properties["%required"] as? UDM.Scalar)?.value shouldBe true

        // Verify key
        val key = record.properties["%key"] as? UDM.Scalar
        key?.value shouldBe "id"
    }

    @Test
    fun `parse employee_roster example file`() {
        val file = File("../../examples/tsch/01_employee_roster.tsch.json")
        if (file.exists()) {
            val parser = TableSchemaParser(file.readText())
            val udm = parser.parse()

            udm.shouldBeInstanceOf<UDM.Object>()
            val schema = udm as UDM.Object
            schema.metadata["__schemaType"] shouldBe "tsch-schema"

            val fields = schema.properties["fields"] as UDM.Array
            fields.elements.size shouldBe 10

            // Verify primaryKey
            val pk = schema.properties["primaryKey"] as? UDM.Scalar
            pk?.value shouldBe "employee_id"

            // Verify foreignKeys exist
            val fks = schema.properties["foreignKeys"] as? UDM.Array
            fks shouldNotBe null
        }
    }

    @Test
    fun `parse invoice_line_items example with composite key`() {
        val file = File("../../examples/tsch/05_invoice_line_items.tsch.json")
        if (file.exists()) {
            val parser = TableSchemaParser(file.readText())
            val udm = parser.parse()

            val schema = udm as UDM.Object
            schema.metadata["__schemaType"] shouldBe "tsch-schema"

            // Verify composite primaryKey
            val pk = schema.properties["primaryKey"] as? UDM.Array
            pk shouldNotBe null
            pk!!.elements.size shouldBe 2
        }
    }

    @Test
    fun `parse supply_chain_orders example with all features`() {
        val file = File("../../examples/tsch/12_supply_chain_orders.tsch.json")
        if (file.exists()) {
            val parser = TableSchemaParser(file.readText())
            val udm = parser.parse()

            val schema = udm as UDM.Object
            schema.metadata["__schemaType"] shouldBe "tsch-schema"

            val fields = schema.properties["fields"] as UDM.Array
            fields.elements.size shouldBe 14

            // Verify multiple foreignKeys
            val fks = schema.properties["foreignKeys"] as? UDM.Array
            fks shouldNotBe null
            fks!!.elements.size shouldBe 2
        }
    }
}

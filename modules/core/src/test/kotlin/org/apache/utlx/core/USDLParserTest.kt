package org.apache.utlx.core

import org.apache.utlx.core.ast.Dialect
import org.apache.utlx.core.ast.Expression
import org.apache.utlx.core.ast.FormatType
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class USDLParserTest {

    @Test
    fun `parse output xsd with usdl dialect`() {
        val source = """
            %utlx 1.0
            input csv
            output xsd %usdl 1.0
            ---
            { result: "test" }
        """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()

        assertTrue(result is org.apache.utlx.core.parser.ParseResult.Success)
        val program = (result as org.apache.utlx.core.parser.ParseResult.Success).program

        assertEquals(FormatType.XSD, program.header.outputFormat.type)
        assertNotNull(program.header.outputFormat.dialect)
        assertEquals("usdl", program.header.outputFormat.dialect!!.name)
        assertEquals("1.0", program.header.outputFormat.dialect!!.version)
    }

    @Test
    fun `parse output jsch with usdl dialect`() {
        val source = """
            %utlx 1.0
            input json
            output jsch %usdl 1.0
            ---
            { result: "test" }
        """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()

        assertTrue(result is org.apache.utlx.core.parser.ParseResult.Success)
        val program = (result as org.apache.utlx.core.parser.ParseResult.Success).program

        assertEquals(FormatType.JSCH, program.header.outputFormat.type)
        assertNotNull(program.header.outputFormat.dialect)
        assertEquals("usdl", program.header.outputFormat.dialect!!.name)
        assertEquals("1.0", program.header.outputFormat.dialect!!.version)
    }

    @Test
    fun `parse output xsd without dialect`() {
        val source = """
            %utlx 1.0
            input csv
            output xsd
            ---
            { result: "test" }
        """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()

        assertTrue(result is org.apache.utlx.core.parser.ParseResult.Success)
        val program = (result as org.apache.utlx.core.parser.ParseResult.Success).program

        assertEquals(FormatType.XSD, program.header.outputFormat.type)
        assertNull(program.header.outputFormat.dialect)
    }

    @Test
    fun `parse object with percent directive keys`() {
        val source = """
            %utlx 1.0
            input csv
            output xsd %usdl 1.0
            ---
            {
              %namespace: "http://example.com",
              %types: {
                Customer: {
                  %kind: "structure",
                  %fields: []
                }
              }
            }
        """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()

        assertTrue(result is org.apache.utlx.core.parser.ParseResult.Success)
        val program = (result as org.apache.utlx.core.parser.ParseResult.Success).program

        // Verify the body is an object literal
        assertTrue(program.body is Expression.ObjectLiteral)
        val obj = program.body as Expression.ObjectLiteral

        // Verify properties with % prefixes are parsed
        val namespaceProperty = obj.properties.find { it.key == "%namespace" }
        assertNotNull(namespaceProperty)

        val typesProperty = obj.properties.find { it.key == "%types" }
        assertNotNull(typesProperty)

        // Verify nested object has %kind
        val typesObj = typesProperty!!.value as Expression.ObjectLiteral
        val customerProp = typesObj.properties.find { it.key == "Customer" }
        assertNotNull(customerProp)

        val customerObj = customerProp!!.value as Expression.ObjectLiteral
        val kindProp = customerObj.properties.find { it.key == "%kind" }
        assertNotNull(kindProp)
        assertTrue(kindProp!!.value is Expression.StringLiteral)
        assertEquals("structure", (kindProp.value as Expression.StringLiteral).value)
    }

    @Test
    fun `parse mixed regular and directive keys`() {
        val source = """
            %utlx 1.0
            input csv
            output xsd %usdl 1.0
            ---
            {
              %namespace: "http://example.com",
              regularKey: "value",
              @attributeKey: "attr",
              %kind: "structure"
            }
        """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()

        assertTrue(result is org.apache.utlx.core.parser.ParseResult.Success)
        val program = (result as org.apache.utlx.core.parser.ParseResult.Success).program

        val obj = program.body as Expression.ObjectLiteral

        // Verify all different key types are parsed
        assertNotNull(obj.properties.find { it.key == "%namespace" })
        assertNotNull(obj.properties.find { it.key == "regularKey" })

        val attrProp = obj.properties.find { it.key == "attributeKey" }
        assertNotNull(attrProp)
        assertTrue(attrProp!!.isAttribute)

        assertNotNull(obj.properties.find { it.key == "%kind" })
    }
}

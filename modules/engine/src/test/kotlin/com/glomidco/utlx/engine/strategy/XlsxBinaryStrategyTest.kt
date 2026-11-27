package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.core.udm.PayloadBytes
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.engine.config.TransformConfig
import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.engine.strategy.compiled.ASTCompiler
import com.glomidco.utlx.formats.json.JSONParser
import com.glomidco.utlx.formats.xlsx.XlsxParser
import com.glomidco.utlx.formats.xlsx.XlsxSerializer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class XlsxBinaryStrategyTest {
    @ParameterizedTest
    @ValueSource(strings = ["TEMPLATE", "COPY", "COMPILED"])
    fun `writes and reads a workbook without decoding ZIP bytes as text`(name: String) {
        val writer = strategy(name)
        val reader = strategy(name)
        try {
            writer.initialize(
                "%utlx 1.0\ninput json\noutput xlsx {sheet: \"Collection\", " +
                    "columns: [\"Name\", \"Remaining\"]}\n---\n\$input",
                TransformConfig(strategy = name)
            )
            reader.initialize(
                "%utlx 1.0\ninput xlsx {sheet: \"Collection\", rowNumbers: true}\noutput json\n---\n\$input",
                TransformConfig(strategy = name)
            )
            val written = writer.execute(PayloadBytes.fromString("[{\"Name\":\"Réserve\",\"Remaining\":0}]"))
            val bytes = assertNotNull(written.outputBytes)
            assertEquals(0x50, bytes[0].toInt())
            assertEquals(1, XlsxParser.parse(bytes, "Collection").elements.size)
            val result = reader.execute(PayloadBytes(bytes, contentType = XlsxSerializer.CONTENT_TYPE))
            val rows = JSONParser(result.output).parse() as UDM.Array
            val row = rows.elements.single() as UDM.Object
            assertEquals("Réserve", (row.get("Name") as UDM.Scalar).asString())
            assertEquals(0.0, (row.get("Remaining") as UDM.Scalar).asNumber())
            assertEquals(2.0, (row.get("_rowNumber") as UDM.Scalar).asNumber())
        } finally {
            writer.shutdown()
            reader.shutdown()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["TEMPLATE", "COPY", "COMPILED"])
    fun `combines named workbook and JSON inputs`(name: String) {
        val strategy = strategy(name)
        val workbook = XlsxSerializer.serialize(
            UDM.Array(listOf(UDM.Object.of("Name" to UDM.Scalar.string("Reserve"))))
        )
        try {
            strategy.initialize(
                "%utlx 1.0\ninput: inventory xlsx, pricing json\noutput json\n---\n" +
                    "{name: first(\$inventory).Name, price: \$pricing.price}",
                TransformConfig(strategy = name)
            )
            val result = strategy.executeNamed(mapOf(
                "inventory" to PayloadBytes(workbook, contentType = XlsxSerializer.CONTENT_TYPE),
                "pricing" to PayloadBytes.fromString("{\"price\":17.5}")
            ))
            val row = JSONParser(result.output).parse() as UDM.Object
            assertEquals("Reserve", (row.get("name") as UDM.Scalar).asString())
            assertEquals(17.5, (row.get("price") as UDM.Scalar).asNumber())
        } finally {
            strategy.shutdown()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["TEMPLATE", "COPY", "COMPILED"])
    fun `interpreter fallback retains workbook input and output`(name: String) {
        val source = "%utlx 1.0\ninput xlsx\noutput xlsx {sheet: \"Result\"}\n---\n" +
            "\$input |> map(row => {Name: first(row.*)})"
        val program = (Parser(Lexer(source).tokenize(), source).parse() as ParseResult.Success).program
        // The wildcard must force fallback; otherwise the compiled success path could mask a regression.
        assertNull(ASTCompiler().compile(program))
        val workbook = XlsxSerializer.serialize(
            UDM.Array(listOf(UDM.Object.of("Name" to UDM.Scalar.string("Reserve"))))
        )
        val strategy = strategy(name)
        try {
            strategy.initialize(source, TransformConfig(strategy = name))
            val result = strategy.execute(PayloadBytes(workbook, contentType = XlsxSerializer.CONTENT_TYPE))
            val rows = XlsxParser.parse(assertNotNull(result.outputBytes), "Result")
            assertEquals(
                UDM.Scalar.string("Reserve"),
                (rows.elements.single() as UDM.Object).get("Name")
            )
        } finally {
            strategy.shutdown()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["TEMPLATE", "COPY", "COMPILED"])
    fun `auto input recognises the workbook media type`(name: String) {
        val strategy = strategy(name)
        val workbook = XlsxSerializer.serialize(
            UDM.Array(listOf(UDM.Object.of("Name" to UDM.Scalar.string("Reserve"))))
        )
        try {
            strategy.initialize(
                "%utlx 1.0\ninput auto\noutput json\n---\n\$input",
                TransformConfig(strategy = name)
            )
            val result = strategy.execute(PayloadBytes(workbook, contentType = XlsxSerializer.CONTENT_TYPE))
            val rows = JSONParser(result.output).parse() as UDM.Array
            assertEquals("Reserve", ((rows.elements.single() as UDM.Object).get("Name") as UDM.Scalar).asString())
        } finally {
            strategy.shutdown()
        }
    }

    private fun strategy(name: String): ExecutionStrategy = when (name) {
        "TEMPLATE" -> TemplateStrategy()
        "COPY" -> CopyStrategy()
        "COMPILED" -> CompiledStrategy()
        else -> error("Unknown strategy $name")
    }
}

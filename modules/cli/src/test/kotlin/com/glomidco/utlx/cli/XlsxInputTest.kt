package com.glomidco.utlx.cli

import com.glomidco.utlx.cli.commands.TransformCommand
import com.glomidco.utlx.cli.service.TransformationService
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.formats.json.JSONParser
import com.glomidco.utlx.formats.xlsx.XlsxParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class XlsxInputTest {
    @TempDir
    lateinit var directory: Path

    private val script = """
        %utlx 1.0
        input xlsx { sheet: "Collection", rowNumbers: true }
        output json
        ---
        ${'$'}input
    """.trimIndent()

    @Test
    fun `transforms binary workbook input through the shared service`() {
        val (output, format) = TransformationService().transform(
            script,
            mapOf("input" to TransformationService.InputData("", null, bytes = workbook()))
        )
        assertEquals("json", format)
        assertPreview(output)
    }

    @Test
    fun `reads a workbook file through the CLI without text decoding`() {
        val input = directory.resolve("collection.xlsx").toFile().apply { writeBytes(workbook()) }
        val transformation = directory.resolve("preview.utlx").toFile().apply { writeText(script) }
        val output = directory.resolve("preview.json").toFile()
        val result = TransformCommand.execute(
            arrayOf(transformation.path, input.path, "--no-capture", "-o", output.path)
        )
        assertEquals(CommandResult.Success, result)
        assertPreview(output.readText())
    }

    @Test
    fun `xlsx remains available as a named input identifier`() {
        val source = """
            %utlx 1.0
            input xlsx json
            output json
            ---
            ${'$'}xlsx
        """.trimIndent()
        val (output, _) = TransformationService().transform(
            source,
            mapOf("xlsx" to TransformationService.InputData("{\"name\":\"Reserve\"}", "json"))
        )
        assertEquals(UDM.Object.of("name" to UDM.Scalar.string("Reserve")), JSONParser(output).parse())
    }

    private fun assertPreview(output: String) {
        val parsed = JSONParser(output).parse() as UDM.Array
        assertEquals(1, parsed.elements.size)
        val row = parsed.elements.single() as UDM.Object
        assertEquals(UDM.Scalar.string("Reserve"), row.get("Bottle"))
        assertEquals(2.0, (row.get("_rowNumber") as UDM.Scalar).asNumber())
    }

    @Test
    fun `binary stdout remains a valid workbook when verbose diagnostics are enabled`() {
        val input = directory.resolve("input.json").toFile().apply {
            writeText("[{\"Bottle\":\"Reserve\"}]")
        }
        val source = directory.resolve("write.utlx").toFile().apply {
            writeText("%utlx 1.0\ninput json\noutput xlsx\n---\n\$input")
        }
        val output = directory.resolve("output.xlsx").toFile()
        val diagnostics = directory.resolve("diagnostics.txt").toFile()
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(
            java, "-jar", System.getProperty("utlx.cli.jar"),
            "transform", source.path, input.path, "--verbose", "--no-capture"
        ).redirectOutput(output).redirectError(diagnostics).start()
        try {
            assertEquals(true, process.waitFor(30, TimeUnit.SECONDS), "The CLI did not terminate.")
            assertEquals(0, process.exitValue(), diagnostics.readText())
            val records = XlsxParser.parse(output)
            assertEquals(1, records.elements.size)
            assertEquals(
                UDM.Scalar.string("Reserve"),
                (records.elements.single() as UDM.Object).get("Bottle")
            )
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    @Test
    fun `failed serialization preserves the previous workbook file`() {
        val input = directory.resolve("rows.json").toFile().apply { writeText("[{\"Name\":\"Reserve\"}]") }
        val source = directory.resolve("write.utlx").toFile().apply {
            writeText("%utlx 1.0\ninput json\noutput xlsx\n---\n\$input")
        }
        val output = directory.resolve("collection.xlsx").toFile().apply { writeText("old output") }
        val args = arrayOf(source.path, input.path, "-o", output.path, "--no-capture")
        assertEquals(CommandResult.Success, TransformCommand.execute(args))
        val previous = output.readBytes()
        assertEquals(1, XlsxParser.parse(previous).elements.size)
        input.writeText("[{\"Nested\":[]}]")
        assertEquals(true, TransformCommand.execute(args) is CommandResult.Failure)
        assertContentEquals(previous, output.readBytes())
    }

    private fun workbook(): ByteArray {
        val entries = mapOf(
            "[Content_Types].xml" to """
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
            """,
            "_rels/.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="xl/workbook.xml"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"/>
                </Relationships>
            """,
            "xl/workbook.xml" to """
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Collection" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
            """,
            "xl/_rels/workbook.xml.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
                </Relationships>
            """,
            "xl/worksheets/sheet1.xml" to """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="inlineStr"><is><t>Bottle</t></is></c></row>
                    <row r="2"><c r="A2" t="inlineStr"><is><t>Reserve</t></is></c></row>
                  </sheetData>
                </worksheet>
            """
        )
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { archive ->
            for ((name, xml) in entries) {
                archive.putNextEntry(ZipEntry(name))
                archive.write(xml.trimIndent().toByteArray(Charsets.UTF_8))
                archive.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}

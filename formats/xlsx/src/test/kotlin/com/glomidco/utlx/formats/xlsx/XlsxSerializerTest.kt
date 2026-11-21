package com.glomidco.utlx.formats.xlsx

import com.glomidco.utlx.core.udm.UDM
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XlsxSerializerTest {
    @Test
    fun `writes typed cells with explicit columns and literal formula text`() {
        val rows = UDM.Array(listOf(UDM.Object.of(
            "Name" to UDM.Scalar.string("Réserve"),
            "Remaining" to UDM.Scalar.number(0),
            "Open" to UDM.Scalar.boolean(false),
            "Unknown" to UDM.Scalar.nullValue(),
            "Literal" to UDM.Scalar.string("=1+1"),
            "Purchased" to UDM.Date(LocalDate.of(2026, 11, 21))
        )))
        val columns = listOf("Name", "Remaining", "Open", "Unknown", "Literal", "Purchased")
        val bytes = XlsxSerializer.serialize(rows, "Collection", columns)
        XSSFWorkbook(bytes.inputStream()).use { workbook ->
            val sheet = workbook.getSheet("Collection")
            assertEquals(columns, sheet.getRow(0).map { it.stringCellValue })
            val row = sheet.getRow(1)
            assertEquals("Réserve", row.getCell(0).stringCellValue)
            assertEquals(CellType.NUMERIC, row.getCell(1).cellType)
            assertEquals(0.0, row.getCell(1).numericCellValue)
            assertEquals(false, row.getCell(2).booleanCellValue)
            assertEquals(CellType.BLANK, row.getCell(3).cellType)
            assertEquals(CellType.STRING, row.getCell(4).cellType)
            assertEquals("=1+1", row.getCell(4).stringCellValue)
            assertEquals(LocalDate.of(2026, 11, 21), row.getCell(5).localDateTimeCellValue.toLocalDate())
        }
    }

    @Test
    fun `preserves numbers beyond Excel precision as text`() {
        val number = BigDecimal("12345678901234567890.123")
        val rows = UDM.Array(listOf(UDM.Object.of("ID" to UDM.Scalar.number(number))))
        val bytes = XlsxSerializer.serialize(rows)
        XSSFWorkbook(bytes.inputStream()).use { workbook ->
            val cell = workbook.getSheetAt(0).getRow(1).getCell(0)
            assertEquals(CellType.STRING, cell.cellType)
            assertEquals(number.toString(), cell.stringCellValue)
        }
    }

    @Test
    fun `creates an empty worksheet with explicitly declared columns`() {
        val bytes = XlsxSerializer.serialize(UDM.Array(emptyList()), columns = listOf("Name"))
        assertEquals(UDM.Array(emptyList()), XlsxParser.parse(bytes))
    }

    @Test
    fun `retains rows flushed to disk and infers columns from later records`() {
        val records = (1..250).map { index ->
            val properties = linkedMapOf<String, UDM>("ID" to UDM.Scalar.number(index))
            if (index == 250) properties["Name"] = UDM.Scalar.string("Last bottle")
            UDM.Object(properties)
        }
        val bytes = XlsxSerializer.serialize(UDM.Array(records))
        XSSFWorkbook(bytes.inputStream()).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals(listOf("ID", "Name"), sheet.getRow(0).map { it.stringCellValue })
            assertEquals(250, sheet.lastRowNum)
            assertEquals(1.0, sheet.getRow(1).getCell(0).numericCellValue)
            assertEquals(CellType.BLANK, sheet.getRow(1).getCell(1).cellType)
            assertEquals(250.0, sheet.getRow(250).getCell(0).numericCellValue)
            assertEquals("Last bottle", sheet.getRow(250).getCell(1).stringCellValue)
        }
    }

    @Test
    fun `rejects oversized worksheets before constructing a workbook`() {
        val record = UDM.Object.of("ID" to UDM.Scalar.number(1))
        assertFailsWith<IllegalArgumentException> {
            XlsxSerializer.serialize(UDM.Array(List(10_001) { record }))
        }
        assertFailsWith<IllegalArgumentException> {
            XlsxSerializer.serialize(UDM.Array(emptyList()), columns = (1..257).map { "Column$it" })
        }
    }

    @Test
    fun `rejects nested cells and duplicate column names`() {
        val rows = UDM.Array(listOf(UDM.Object.of("Nested" to UDM.Array(emptyList()))))
        assertFailsWith<IllegalArgumentException> { XlsxSerializer.serialize(rows) }
        assertFailsWith<IllegalArgumentException> {
            XlsxSerializer.serialize(UDM.Array(emptyList()), columns = listOf("Name", "Name"))
        }
    }
}

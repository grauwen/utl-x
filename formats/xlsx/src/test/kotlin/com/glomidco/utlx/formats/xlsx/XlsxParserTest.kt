package com.glomidco.utlx.formats.xlsx

import com.glomidco.utlx.core.udm.UDM
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XlsxParserTest {
    @Test
    fun `reads collection sheet and preserves unknowns zero and source rows`() {
        val bytes = workbook {
            createSheet("Wishlist").createRow(0).createCell(0).setCellValue("Wish")
            val collection = createSheet("Collection")
            val header = collection.createRow(0)
            val headers = listOf("Bottle", "Size (mL)", "Remaining (mL)", "Volume approximate", "Purchased on")
            headers.forEachIndexed { index, name ->
                header.createCell(index).setCellValue(name)
            }
            val dateStyle = createCellStyle().apply { dataFormat = createDataFormat().getFormat("yyyy-mm-dd") }
            collection.createRow(1).apply {
                createCell(0).setCellValue("001 Reserve")
                createCell(2).setCellValue(0.0)
                createCell(3).setCellValue(false)
                createCell(4).apply {
                    setCellValue(LocalDate.of(2026, 10, 23))
                    cellStyle = dateStyle
                }
            }
            collection.createRow(3).apply {
                createCell(0).setCellValue("Batch 002")
                createCell(1).apply {
                    setCellFormula("350*2")
                    setCellValue(700.0)
                }
            }
        }

        val records = XlsxParser.parse(bytes, sheetName = "Collection", rowNumbers = true).elements
        assertEquals(
            listOf(
                row("001 Reserve", null, 0.0, false, "2026-10-23", 2),
                row("Batch 002", 700.0, null, null, null, 4)
            ),
            records
        )
    }

    @Test
    fun `defaults to the first visible sheet and reports a missing requested sheet`() {
        val bytes = workbook {
            createSheet("Hidden").createRow(0).createCell(0).setCellValue("Ignored")
            createSheet("Inventory").createRow(0).createCell(0).setCellValue("Name")
            setSheetHidden(0, true)
        }
        assertEquals(UDM.Array(emptyList()), XlsxParser.parse(bytes))
        val error = assertFailsWith<IllegalArgumentException> { XlsxParser.parse(bytes, "Collection") }
        assertTrue(error.message.orEmpty().contains("Collection"))
    }

    @Test
    fun `rejects a populated cell that has no header`() {
        val bytes = workbook {
            val sheet = createSheet("Inventory")
            sheet.createRow(0).createCell(0).setCellValue("Name")
            sheet.createRow(1).createCell(1).setCellValue("Unmapped")
        }
        val error = assertFailsWith<IllegalArgumentException> { XlsxParser.parse(bytes) }
        assertTrue(error.message.orEmpty().contains("B2"))
    }

    private fun workbook(populate: XSSFWorkbook.() -> Unit): ByteArray =
        XSSFWorkbook().use { workbook ->
            workbook.populate()
            ByteArrayOutputStream().also(workbook::write).toByteArray()
        }

    private fun row(
        name: String,
        size: Double?,
        remaining: Double?,
        approximate: Boolean?,
        purchased: String?,
        number: Int
    ) =
        UDM.Object.of(
            "Bottle" to UDM.Scalar.string(name),
            "Size (mL)" to UDM.Scalar(size),
            "Remaining (mL)" to UDM.Scalar(remaining),
            "Volume approximate" to UDM.Scalar(approximate),
            "Purchased on" to UDM.Scalar(purchased),
            "_rowNumber" to UDM.Scalar.number(number)
        )
}

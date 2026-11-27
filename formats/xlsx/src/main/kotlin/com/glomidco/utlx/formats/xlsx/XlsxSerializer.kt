package com.glomidco.utlx.formats.xlsx

import com.glomidco.utlx.core.udm.UDM
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/** Writes an array of records to one worksheet with typed scalar cells. */
object XlsxSerializer {
    const val CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val MAX_ROWS = 10_000
    private const val MAX_COLUMNS = 256

    fun serialize(udm: UDM, sheetName: String = "Sheet1", columns: List<String>? = null): ByteArray {
        WorkbookUtil.validateSheetName(sheetName)
        val rows = (udm as? UDM.Array)?.elements
            ?: throw IllegalArgumentException("XLSX output requires an array of records.")
        require(rows.size <= MAX_ROWS) { "XLSX output supports up to $MAX_ROWS data rows." }
        val records = rows.mapIndexed { index, row ->
            row as? UDM.Object ?: throw IllegalArgumentException("XLSX record ${index + 1} must be an object.")
        }
        val headers = columns ?: records.flatMap { it.properties.keys }.distinct()
        require(headers.isNotEmpty()) { "XLSX output needs at least one column. Set columns for an empty array." }
        require(headers.size <= MAX_COLUMNS) { "XLSX output supports up to $MAX_COLUMNS columns." }
        require(headers.all { it.isNotBlank() }) { "XLSX column names must be non-empty." }
        require(headers.map { it.trim() }.distinct().size == headers.size) { "XLSX column names must be distinct." }

        // Flush older rows to disk instead of retaining a second full workbook model in the JVM heap.
        return SXSSFWorkbook(100).use { workbook ->
            workbook.setCompressTempFiles(true)
            val sheet = workbook.createSheet(sheetName)
            val header = sheet.createRow(0)
            headers.forEachIndexed { index, name -> header.createCell(index).setCellValue(name) }
            val dateStyle = workbook.createCellStyle().apply {
                dataFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd")
            }
            records.forEachIndexed { index, record ->
                val row = sheet.createRow(index + 1)
                headers.forEachIndexed { column, name ->
                    val value = record.properties[name] ?: UDM.Scalar.nullValue()
                    val cell = row.createCell(column)
                    when (value) {
                        is UDM.Date -> {
                            cell.setCellValue(value.date)
                            cell.cellStyle = dateStyle
                        }
                        is UDM.Scalar -> when (val scalar = value.value) {
                            null -> Unit
                            is String -> cell.setCellValue(scalar)
                            is Boolean -> cell.setCellValue(scalar)
                            is Number -> {
                                require(scalar !is Double || scalar.isFinite()) {
                                    "XLSX number at ${cell.address} must be finite."
                                }
                                require(scalar !is Float || scalar.isFinite()) {
                                    "XLSX number at ${cell.address} must be finite."
                                }
                                val number = BigDecimal(scalar.toString())
                                val excelValue = number.toDouble()
                                if (number.stripTrailingZeros().precision() > 15 || !excelValue.isFinite() ||
                                    (excelValue == 0.0 && number.signum() != 0)
                                ) {
                                    // Excel stores only 15 significant decimal digits.
                                    cell.setCellValue(scalar.toString())
                                } else {
                                    cell.setCellValue(excelValue)
                                }
                            }
                            else -> throw IllegalArgumentException("Unsupported XLSX value at ${cell.address}.")
                        }
                        is UDM.DateTime -> cell.setCellValue(value.instant.toString())
                        is UDM.LocalDateTime -> cell.setCellValue(value.dateTime.toString())
                        is UDM.Time -> cell.setCellValue(value.time.toString())
                        else -> throw IllegalArgumentException(
                            "XLSX cell ${cell.address} requires a scalar value. Flatten nested records first."
                        )
                    }
                }
            }
            ByteArrayOutputStream().also(workbook::write).toByteArray()
        }
    }
}

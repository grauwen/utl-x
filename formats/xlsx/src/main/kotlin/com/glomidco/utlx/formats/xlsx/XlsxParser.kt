package com.glomidco.utlx.formats.xlsx

import com.glomidco.utlx.core.udm.UDM
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.time.LocalTime
import java.util.zip.ZipFile

/** Reads a worksheet as an array of records keyed by the first row's column names. */
object XlsxParser {
    private const val MAX_FILE_BYTES = 128 * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 128 * 1024 * 1024
    private const val MAX_ENTRIES = 16_384
    private const val MAX_COLUMNS = 256
    private const val MAX_ROWS = 10_000
    private const val ROW_NUMBER = "_rowNumber"

    /** Spool binary input to disk. The caller owns the returned file. */
    fun spool(input: InputStream): File {
        val file = Files.createTempFile("utlx-workbook-", ".xlsx").toFile()
        try {
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    validateSize(total)
                    output.write(buffer, 0, count)
                }
            }
            return file
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    fun parse(bytes: ByteArray, sheetName: String? = null, rowNumbers: Boolean = false): UDM.Array {
        validateSize(bytes.size.toLong())
        val file = spool(bytes.inputStream())
        try {
            return parse(file, sheetName, rowNumbers)
        } finally {
            file.delete()
        }
    }

    fun parse(file: File, sheetName: String? = null, rowNumbers: Boolean = false): UDM.Array {
        validateSize(Files.size(file.toPath()))
        validateExpandedSize(file)
        return OPCPackage.open(file, PackageAccess.READ).use { archive ->
            XSSFWorkbook(archive).use { workbook -> readWorksheet(workbook, sheetName, rowNumbers) }
        }
    }

    private fun readWorksheet(workbook: XSSFWorkbook, sheetName: String?, rowNumbers: Boolean): UDM.Array {
        val sheet = if (sheetName != null) {
            workbook.getSheet(sheetName)
                ?: throw IllegalArgumentException("XLSX worksheet '$sheetName' was not found.")
        } else {
            (0 until workbook.numberOfSheets)
                .firstOrNull { !workbook.isSheetHidden(it) && !workbook.isSheetVeryHidden(it) }
                ?.let(workbook::getSheetAt)
                ?: throw IllegalArgumentException("The XLSX workbook has no visible worksheet.")
        }
        val header = requireNotNull(sheet.getRow(0)) { "XLSX worksheet '${sheet.sheetName}' needs headers in row 1." }
        val headers = linkedMapOf<Int, String>()
        val names = mutableSetOf<String>()
        for (cell in header) {
            if (cell.cellType == CellType.BLANK) continue
            require(cell.cellType == CellType.STRING) { "XLSX header ${cell.address} must be text." }
            val name = cell.stringCellValue.trim()
            if (name.isEmpty()) continue
            require(cell.columnIndex < MAX_COLUMNS) { "XLSX supports columns through column IV (256)." }
            require(names.add(name)) { "Duplicate XLSX header '$name' at ${cell.address}." }
            require(!rowNumbers || name != ROW_NUMBER) {
                "XLSX header '$ROW_NUMBER' is reserved when rowNumbers is enabled."
            }
            headers[cell.columnIndex] = name
        }
        require(headers.isNotEmpty()) { "XLSX worksheet '${sheet.sheetName}' needs headers in row 1." }

        val records = mutableListOf<UDM>()
        for (row in sheet) {
            if (row.rowNum == 0) continue
            val values = mutableMapOf<Int, UDM>()
            for (cell in row) {
                val value = readCell(cell as XSSFCell)
                if (value.value == null || value.value == "") continue
                require(cell.columnIndex in headers) { "XLSX cell ${cell.address} has no column header." }
                values[cell.columnIndex] = value
            }
            if (values.isEmpty()) continue
            require(records.size < MAX_ROWS) { "XLSX supports up to $MAX_ROWS data rows per worksheet." }
            val record = headers.entries.associateTo(linkedMapOf()) { (column, name) ->
                name to (values[column] ?: UDM.Scalar.nullValue())
            }
            if (rowNumbers) record[ROW_NUMBER] = UDM.Scalar.number(row.rowNum + 1)
            records.add(UDM.Object(record))
        }
        return UDM.Array(records)
    }

    private fun readCell(cell: XSSFCell): UDM.Scalar {
        val type = if (cell.cellType == CellType.FORMULA) {
            if (cell.rawValue == null) return UDM.Scalar.nullValue()
            cell.cachedFormulaResultType
        } else {
            cell.cellType
        }
        return when (type) {
            CellType.STRING -> UDM.Scalar.string(cell.stringCellValue)
            CellType.BOOLEAN -> UDM.Scalar.boolean(cell.booleanCellValue)
            CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(cell)) {
                val date = cell.localDateTimeCellValue
                val isoDate = if (date.toLocalTime() == LocalTime.MIDNIGHT) date.toLocalDate() else date
                UDM.Scalar.string(isoDate.toString())
            } else {
                UDM.Scalar.number(cell.numericCellValue)
            }
            CellType.BLANK -> UDM.Scalar.nullValue()
            CellType.ERROR -> throw IllegalArgumentException(
                "XLSX cell ${cell.address} contains Excel error ${FormulaError.forInt(cell.errorCellValue).string}."
            )
            else -> throw IllegalArgumentException("Unsupported XLSX cell type at ${cell.address}: $type.")
        }
    }

    private fun validateSize(bytes: Long) {
        require(bytes <= MAX_FILE_BYTES) { "XLSX input exceeds the ${MAX_FILE_BYTES / (1024 * 1024)} MiB file limit." }
    }

    // Bound the whole package, including photos and sheets the caller does not select.
    private fun validateExpandedSize(file: File) {
        ZipFile(file).use { zip ->
            require(zip.size() in 1..MAX_ENTRIES) {
                "XLSX input must contain between 1 and $MAX_ENTRIES archive entries."
            }
            val buffer = ByteArray(8192)
            var expanded = 0
            for (entry in zip.entries()) {
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        expanded += count
                        require(expanded <= MAX_EXPANDED_BYTES) {
                            "XLSX input exceeds the 128 MiB expanded file limit."
                        }
                    }
                }
            }
        }
    }
}

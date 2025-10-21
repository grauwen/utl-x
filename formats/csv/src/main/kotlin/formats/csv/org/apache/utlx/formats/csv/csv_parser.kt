package org.apache.utlx.formats.csv

import org.apache.utlx.core.udm.UDM
import java.io.Reader
import java.io.StringReader

/**
 * CSV Parser - Converts CSV to UDM
 * 
 * Mapping (with headers):
 * - Each row → UDM.Object (keys from header row)
 * - Multiple rows → UDM.Array of UDM.Object
 * - Numeric values → UDM.Scalar(number) if parseable
 * 
 * Mapping (without headers):
 * - Each row → UDM.Array of UDM.Scalar
 * - Multiple rows → UDM.Array of UDM.Array
 * 
 * Example with headers:
 * Name,Age,Active
 * Alice,30,true
 * Bob,25,false
 * 
 * Becomes:
 * UDM.Array([
 *   UDM.Object({"Name": "Alice", "Age": 30, "Active": true}),
 *   UDM.Object({"Name": "Bob", "Age": 25, "Active": false})
 * ])
 */
class CSVParser(
    private val source: Reader,
    private val dialect: CSVDialect = CSVDialect.DEFAULT
) {
    private var current = 0
    private var line = 1
    private var column = 1
    private val text = run {
        // CSV may begin with BOM (U+FEFF) - strip it if present
        val rawText = source.readText()
        if (rawText.isNotEmpty() && rawText[0] == '\uFEFF') {
            rawText.substring(1)
        } else {
            rawText
        }
    }
    
    constructor(csv: String, dialect: CSVDialect = CSVDialect.DEFAULT) : 
        this(StringReader(csv), dialect)
    
    /**
     * Parse CSV to UDM
     */
    fun parse(hasHeaders: Boolean = true): UDM {
        val rows = mutableListOf<List<String>>()
        
        // Parse all rows
        while (!isAtEnd()) {
            val row = parseRow()
            if (row.isNotEmpty()) {
                rows.add(row)
            }
            
            // Skip to next line
            while (!isAtEnd() && (peek() == '\n' || peek() == '\r')) {
                if (advance() == '\n') {
                    line++
                    column = 1
                }
            }
        }
        
        if (rows.isEmpty()) {
            return UDM.Array.empty()
        }
        
        return if (hasHeaders) {
            parseWithHeaders(rows)
        } else {
            parseWithoutHeaders(rows)
        }
    }
    
    private fun parseWithHeaders(rows: List<List<String>>): UDM {
        if (rows.isEmpty()) return UDM.Array.empty()
        
        val headers = rows[0].map { it.trim() }
        val dataRows = rows.drop(1)
        
        val objects = dataRows.map { row ->
            val properties = mutableMapOf<String, UDM>()
            
            headers.forEachIndexed { index, header ->
                val value = if (index < row.size) row[index] else ""
                properties[header] = parseValue(value)
            }
            
            UDM.Object(properties)
        }
        
        return UDM.Array(objects)
    }
    
    private fun parseWithoutHeaders(rows: List<List<String>>): UDM {
        val arrays = rows.map { row ->
            UDM.Array(row.map { parseValue(it) })
        }
        return UDM.Array(arrays)
    }
    
    private fun parseRow(): List<String> {
        val fields = mutableListOf<String>()
        
        while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
            fields.add(parseField())
            
            if (peek() == dialect.delimiter) {
                advance() // consume delimiter
            } else if (peek() != '\n' && peek() != '\r' && !isAtEnd()) {
                throw CSVParseException("Expected delimiter or newline", line, column)
            }
        }
        
        return fields
    }
    
    private fun parseField(): String {
        // Check if quoted field
        if (peek() == dialect.quote) {
            return parseQuotedField()
        }
        
        // Unquoted field - read until delimiter or newline
        val sb = StringBuilder()
        while (!isAtEnd() && peek() != dialect.delimiter && peek() != '\n' && peek() != '\r') {
            sb.append(advance())
        }
        
        return sb.toString().trim()
    }
    
    private fun parseQuotedField(): String {
        val sb = StringBuilder()
        
        advance() // opening quote
        
        while (!isAtEnd()) {
            val c = peek()
            
            if (c == dialect.quote) {
                advance() // consume quote
                
                // Check for escaped quote (two quotes in a row)
                if (peek() == dialect.quote) {
                    sb.append(dialect.quote)
                    advance()
                } else {
                    // End of quoted field
                    break
                }
            } else {
                sb.append(advance())
                if (c == '\n') {
                    line++
                    column = 1
                }
            }
        }
        
        return sb.toString()
    }
    
    private fun parseValue(str: String): UDM.Scalar {
        val trimmed = str.trim()
        
        // Try boolean
        when (trimmed.lowercase()) {
            "true" -> return UDM.Scalar.boolean(true)
            "false" -> return UDM.Scalar.boolean(false)
        }
        
        // Try null
        if (trimmed.isEmpty() || trimmed.lowercase() in setOf("null", "nil", "n/a")) {
            return UDM.Scalar.nullValue()
        }
        
        // Try number
        trimmed.toDoubleOrNull()?.let {
            return UDM.Scalar.number(it)
        }
        
        // Default to string
        return UDM.Scalar.string(trimmed)
    }
    
    private fun peek(): Char {
        if (isAtEnd()) return '\u0000'
        return text[current]
    }
    
    private fun advance(): Char {
        val c = text[current]
        current++
        column++
        return c
    }
    
    private fun isAtEnd(): Boolean = current >= text.length
}

/**
 * CSV Dialect - defines CSV format variations
 */
data class CSVDialect(
    val delimiter: Char = ',',
    val quote: Char = '"',
    val escape: Char = '"',  // Standard CSV uses quote doubling
    val lineTerminator: String = "\n"
) {
    companion object {
        /**
         * Standard RFC 4180 CSV
         */
        val DEFAULT = CSVDialect(
            delimiter = ',',
            quote = '"'
        )
        
        /**
         * Tab-separated values (TSV)
         */
        val TSV = CSVDialect(
            delimiter = '\t',
            quote = '"'
        )
        
        /**
         * Semicolon-delimited (common in European locales)
         */
        val SEMICOLON = CSVDialect(
            delimiter = ';',
            quote = '"'
        )
        
        /**
         * Pipe-delimited
         */
        val PIPE = CSVDialect(
            delimiter = '|',
            quote = '"'
        )
        
        /**
         * Excel-style CSV
         */
        val EXCEL = CSVDialect(
            delimiter = ',',
            quote = '"',
            lineTerminator = "\r\n"
        )
    }
}

/**
 * CSV parse exception
 */
class CSVParseException(
    message: String,
    val line: Int,
    val column: Int
) : Exception("CSV parse error at $line:$column - $message")

/**
 * Convenience object for CSV operations
 */
object CSV {
    /**
     * Parse CSV string to UDM (with headers by default)
     */
    fun parse(csv: String, hasHeaders: Boolean = true, dialect: CSVDialect = CSVDialect.DEFAULT): UDM {
        return CSVParser(csv, dialect).parse(hasHeaders)
    }
    
    /**
     * Parse TSV (tab-separated values)
     */
    fun parseTSV(tsv: String, hasHeaders: Boolean = true): UDM {
        return CSVParser(tsv, CSVDialect.TSV).parse(hasHeaders)
    }
}

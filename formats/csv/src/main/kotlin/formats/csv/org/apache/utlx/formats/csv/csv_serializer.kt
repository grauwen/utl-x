package org.apache.utlx.formats.csv

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.interpreter.RuntimeValue
import java.io.Writer
import java.io.StringWriter

/**
 * CSV Serializer - Converts UDM or RuntimeValue to CSV
 * 
 * Expected input structure (with headers):
 * UDM.Array([
 *   UDM.Object({"Name": "Alice", "Age": 30}),
 *   UDM.Object({"Name": "Bob", "Age": 25})
 * ])
 * 
 * Output:
 * Name,Age
 * Alice,30
 * Bob,25
 * 
 * Without headers:
 * UDM.Array([
 *   UDM.Array([Scalar("Alice"), Scalar(30)]),
 *   UDM.Array([Scalar("Bob"), Scalar(25)])
 * ])
 * 
 * Output:
 * Alice,30
 * Bob,25
 */
class CSVSerializer(
    private val dialect: CSVDialect = CSVDialect.DEFAULT,
    private val includeHeaders: Boolean = true,
    private val includeBOM: Boolean = false
) {
    /**
     * Serialize UDM to CSV string
     */
    fun serialize(udm: UDM): String {
        val writer = StringWriter()
        // Add BOM if requested (useful for Excel UTF-8 compatibility)
        if (includeBOM) {
            writer.write("\uFEFF")
        }
        serialize(udm, writer)
        return writer.toString()
    }
    
    /**
     * Serialize RuntimeValue to CSV string
     */
    fun serialize(value: RuntimeValue): String {
        val writer = StringWriter()
        // Add BOM if requested (useful for Excel UTF-8 compatibility)
        if (includeBOM) {
            writer.write("\uFEFF")
        }
        serialize(value, writer)
        return writer.toString()
    }
    
    /**
     * Serialize UDM to Writer
     */
    fun serialize(udm: UDM, writer: Writer) {
        when (udm) {
            is UDM.Array -> serializeArray(udm, writer)
            is UDM.Object -> {
                // Check for special {headers: [...], rows: [...]} pattern
                if (udm.properties.containsKey("headers") && udm.properties.containsKey("rows")) {
                    val headersValue = udm.properties["headers"]
                    val rowsValue = udm.properties["rows"]

                    if (headersValue is UDM.Array && rowsValue is UDM.Array) {
                        // Write headers
                        if (includeHeaders) {
                            val headers = headersValue.elements.map { extractValue(it) }
                            writeRow(writer, headers)
                        }

                        // Write rows
                        rowsValue.elements.forEach { row ->
                            if (row is UDM.Array) {
                                val values = row.elements.map { extractValue(it) }
                                writeRow(writer, values)
                            }
                        }
                        return
                    }
                }

                // Single object - treat as one-row table
                val headers = udm.keys().toList()
                if (includeHeaders) {
                    writeRow(writer, headers)
                }
                val values = headers.map { extractValue(udm.get(it)) }
                writeRow(writer, values)
            }
            else -> {
                // Single value - one cell
                writer.write(escapeField(extractValue(udm)))
            }
        }
    }
    
    /**
     * Serialize RuntimeValue to Writer
     */
    fun serialize(value: RuntimeValue, writer: Writer) {
        when (value) {
            is RuntimeValue.ArrayValue -> serializeRuntimeArray(value, writer)
            is RuntimeValue.ObjectValue -> {
                // Check for special {headers: [...], rows: [...]} pattern
                if (value.properties.containsKey("headers") && value.properties.containsKey("rows")) {
                    val headersValue = value.properties["headers"]
                    val rowsValue = value.properties["rows"]

                    if (headersValue is RuntimeValue.ArrayValue && rowsValue is RuntimeValue.ArrayValue) {
                        // Write headers
                        if (includeHeaders) {
                            val headers = headersValue.elements.map { extractRuntimeValue(it) }
                            writeRow(writer, headers)
                        }

                        // Write rows
                        rowsValue.elements.forEach { row ->
                            if (row is RuntimeValue.ArrayValue) {
                                val values = row.elements.map { extractRuntimeValue(it) }
                                writeRow(writer, values)
                            }
                        }
                        return
                    }
                }

                // Single object - treat as one-row table
                val headers = value.properties.keys.toList()
                if (includeHeaders) {
                    writeRow(writer, headers)
                }
                val values = headers.map { extractRuntimeValue(value.properties[it]) }
                writeRow(writer, values)
            }
            is RuntimeValue.UDMValue -> serialize(value.udm, writer)
            else -> writer.write(escapeField(extractRuntimeValue(value)))
        }
    }
    
    private fun serializeArray(array: UDM.Array, writer: Writer) {
        if (array.isEmpty()) return
        
        val first = array.elements.first()
        
        when (first) {
            is UDM.Object -> {
                // Array of objects - treat as table with headers
                val headers = first.keys().toList()
                
                if (includeHeaders) {
                    writeRow(writer, headers)
                }
                
                array.elements.forEach { element ->
                    if (element is UDM.Object) {
                        val values = headers.map { extractValue(element.get(it)) }
                        writeRow(writer, values)
                    }
                }
            }
            
            is UDM.Array -> {
                // Array of arrays - just write rows
                array.elements.forEach { element ->
                    if (element is UDM.Array) {
                        val values = element.elements.map { extractValue(it) }
                        writeRow(writer, values)
                    }
                }
            }
            
            else -> {
                // Array of scalars - single column
                if (includeHeaders) {
                    writer.write("value")
                    writer.write(dialect.lineTerminator)
                }
                array.elements.forEach { element ->
                    writer.write(escapeField(extractValue(element)))
                    writer.write(dialect.lineTerminator)
                }
            }
        }
    }
    
    private fun serializeRuntimeArray(array: RuntimeValue.ArrayValue, writer: Writer) {
        if (array.elements.isEmpty()) return
        
        val first = array.elements.first()
        
        when (first) {
            is RuntimeValue.ObjectValue -> {
                // Array of objects
                val headers = first.properties.keys.toList()
                
                if (includeHeaders) {
                    writeRow(writer, headers)
                }
                
                array.elements.forEach { element ->
                    if (element is RuntimeValue.ObjectValue) {
                        val values = headers.map { extractRuntimeValue(element.properties[it]) }
                        writeRow(writer, values)
                    }
                }
            }
            
            is RuntimeValue.ArrayValue -> {
                // Array of arrays
                array.elements.forEach { element ->
                    if (element is RuntimeValue.ArrayValue) {
                        val values = element.elements.map { extractRuntimeValue(it) }
                        writeRow(writer, values)
                    }
                }
            }
            
            else -> {
                // Array of scalars
                if (includeHeaders) {
                    writer.write("value")
                    writer.write(dialect.lineTerminator)
                }
                array.elements.forEach { element ->
                    writer.write(escapeField(extractRuntimeValue(element)))
                    writer.write(dialect.lineTerminator)
                }
            }
        }
    }
    
    private fun writeRow(writer: Writer, values: List<String>) {
        values.forEachIndexed { index, value ->
            writer.write(escapeField(value))
            if (index < values.size - 1) {
                writer.write(dialect.delimiter.toString())
            }
        }
        writer.write(dialect.lineTerminator)
    }
    
    private fun escapeField(value: String): String {
        // Check if field needs quoting
        val needsQuoting = value.contains(dialect.delimiter) ||
                          value.contains(dialect.quote) ||
                          value.contains('\n') ||
                          value.contains('\r') ||
                          value.startsWith(" ") ||
                          value.endsWith(" ")
        
        if (!needsQuoting) {
            return value
        }
        
        // Quote and escape internal quotes
        val escaped = value.replace(
            dialect.quote.toString(),
            "${dialect.quote}${dialect.quote}"
        )
        
        return "${dialect.quote}$escaped${dialect.quote}"
    }
    
    private fun extractValue(udm: UDM?): String {
        return when (udm) {
            null -> ""
            is UDM.Scalar -> {
                when (val v = udm.value) {
                    null -> ""
                    is Boolean -> v.toString()
                    is Number -> {
                        if (v is Double && v % 1.0 == 0.0) {
                            v.toLong().toString()
                        } else {
                            v.toString()
                        }
                    }
                    else -> v.toString()
                }
            }
            is UDM.DateTime -> udm.toISOString()
            else -> udm.toString()
        }
    }
    
    private fun extractRuntimeValue(value: RuntimeValue?): String {
        return when (value) {
            null -> ""
            is RuntimeValue.StringValue -> value.value
            is RuntimeValue.NumberValue -> {
                if (value.value % 1.0 == 0.0) {
                    value.value.toLong().toString()
                } else {
                    value.value.toString()
                }
            }
            is RuntimeValue.BooleanValue -> value.value.toString()
            is RuntimeValue.NullValue -> ""
            is RuntimeValue.UDMValue -> extractValue(value.udm)
            else -> value.toString()
        }
    }
}

/**
 * Convenience extension for CSV serialization
 */
object CSVFormat {
    /**
     * Serialize to CSV with headers
     */
    fun stringify(udm: UDM, dialect: CSVDialect = CSVDialect.DEFAULT): String {
        return CSVSerializer(dialect, includeHeaders = true).serialize(udm)
    }
    
    fun stringify(value: RuntimeValue, dialect: CSVDialect = CSVDialect.DEFAULT): String {
        return CSVSerializer(dialect, includeHeaders = true).serialize(value)
    }
    
    /**
     * Serialize to CSV without headers
     */
    fun stringifyWithoutHeaders(udm: UDM, dialect: CSVDialect = CSVDialect.DEFAULT): String {
        return CSVSerializer(dialect, includeHeaders = false).serialize(udm)
    }
    
    fun stringifyWithoutHeaders(value: RuntimeValue, dialect: CSVDialect = CSVDialect.DEFAULT): String {
        return CSVSerializer(dialect, includeHeaders = false).serialize(value)
    }
    
    /**
     * Serialize to TSV (tab-separated values)
     */
    fun stringifyTSV(udm: UDM): String {
        return CSVSerializer(CSVDialect.TSV, includeHeaders = true).serialize(udm)
    }

    /**
     * Serialize to CSV with BOM (for Excel UTF-8 compatibility)
     * BOM (Byte Order Mark) helps Excel recognize UTF-8 encoded CSV files
     */
    fun stringifyWithBOM(udm: UDM, dialect: CSVDialect = CSVDialect.DEFAULT): String {
        return CSVSerializer(dialect, includeHeaders = true, includeBOM = true).serialize(udm)
    }

    fun stringifyWithBOM(value: RuntimeValue, dialect: CSVDialect = CSVDialect.DEFAULT): String {
        return CSVSerializer(dialect, includeHeaders = true, includeBOM = true).serialize(value)
    }

    /**
     * Parse CSV string to UDM
     */
    fun parse(csv: String, hasHeaders: Boolean = true, dialect: CSVDialect = CSVDialect.DEFAULT): UDM {
        return CSV.parse(csv, hasHeaders, dialect)
    }
}

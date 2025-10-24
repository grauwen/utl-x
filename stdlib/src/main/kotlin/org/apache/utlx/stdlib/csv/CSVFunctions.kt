/**
 * Advanced CSV Functions for UTL-X Standard Library
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/csv/CSVFunctions.kt
 * 
 * Provides spreadsheet-like operations for CSV data manipulation.
 * Functions are designed for enterprise data transformation scenarios.
 * 
 * Function Categories:
 * - Structure Access (rows, columns, cells)
 * - Transformations (transpose, pivot, unpivot)
 * - Aggregations (groupBy, summarize)
 * - Joins (merge, vlookup, index/match)
 * - Filtering & Sorting
 * - Column Operations
 */

package org.apache.utlx.stdlib.csv

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Advanced CSV Functions for data manipulation and analysis
 * 
 * All functions follow UDM compliance pattern for stdlib registration.
 * Provides spreadsheet-like operations for CSV data transformation.
 */
object CSVFunctions {

    @UTLXFunction(
        description = "Get all rows from CSV data",
        minArgs = 1,
        maxArgs = 1,
        category = "CSV",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "csvRows(...) => result",
        notes = "Example:\n```\ncsvRows(csvData)\n// Returns: UDM.Array of row objects\n```",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Get all rows from CSV data
     * 
     * @param args List containing: [csv]
     * @return UDM Array of row objects
     * 
     * Example:
     * ```
     * csvRows(csvData)
     * // Returns: UDM.Array of row objects
     * ```
     */
    fun csvRows(args: List<UDM>): UDM {
        requireArgs(args, 1, "csvRows")
        val csv = args[0]
        
        return try {
            when (csv) {
                is UDM.Scalar -> {
                    // Parse CSV string
                    val csvData = parseCSVString(csv.asString())
                    UDM.Array(csvData.rows.map { row ->
                        UDM.Object(row.mapValues { UDM.Scalar(it.value) })
                    })
                }
                is UDM.Object -> {
                    // Already parsed structure: { headers: [...], rows: [{...}] }
                    csv.properties["rows"] as? UDM.Array 
                        ?: throw FunctionArgumentException("CSV object must have 'rows' property")
                }
                else -> throw FunctionArgumentException("Expected CSV string or object")
            }
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to get CSV rows: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Get all column names/headers from CSV data",
        minArgs = 1,
        maxArgs = 1,
        category = "CSV",
        parameters = [
            "array: Input array to process",
        "columnName: Columnname value"
        ],
        returns = "Result of the operation",
        example = "csvColumns(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Get all column names/headers from CSV data
     * 
     * @param args List containing: [csv]
     * @return UDM Array of column names
     */
    fun csvColumns(args: List<UDM>): UDM {
        requireArgs(args, 1, "csvColumns")
        val csv = args[0]
        
        return try {
            when (csv) {
                is UDM.Scalar -> {
                    val csvData = parseCSVString(csv.asString())
                    UDM.Array(csvData.headers.map { UDM.Scalar(it) })
                }
                is UDM.Object -> {
                    csv.properties["headers"] as? UDM.Array 
                        ?: throw FunctionArgumentException("CSV object must have 'headers' property")
                }
                else -> throw FunctionArgumentException("Expected CSV string or object")
            }
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to get CSV columns: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Get a specific column as array",
        minArgs = 1,
        maxArgs = 1,
        category = "CSV",
        parameters = [
            "array: Input array to process",
        "columnName: Columnname value"
        ],
        returns = "Result of the operation",
        example = "csvColumn(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Get a specific column as array
     * 
     * @param args List containing: [csv, columnName]
     * @return UDM Array of values from that column
     */
    fun csvColumn(args: List<UDM>): UDM {
        requireArgs(args, 2, "csvColumn")
        val csv = args[0]
        val columnName = args[1].asString()
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                is UDM.Object -> parseCSVFromUDM(csv)
                else -> throw FunctionArgumentException("Expected CSV string or object")
            }
            
            if (columnName !in csvData.headers) {
                throw FunctionArgumentException("Column '$columnName' not found")
            }
            
            UDM.Array(csvData.rows.map { row ->
                UDM.Scalar(row[columnName] ?: "")
            })
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to get CSV column: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Get a specific row by index",
        minArgs = 3,
        maxArgs = 3,
        category = "CSV",
        parameters = [
            "csv: Csv value",
        "index: Index value",
        "columnName: Columnname value"
        ],
        returns = "Result of the operation",
        example = "csvRow(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Get a specific row by index
     * 
     * @param args List containing: [csv, index]
     * @return UDM Object with column names as keys
     */
    fun csvRow(args: List<UDM>): UDM {
        requireArgs(args, 2, "csvRow")
        val csv = args[0]
        val index = args[1].asInt()
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                is UDM.Object -> parseCSVFromUDM(csv)
                else -> throw FunctionArgumentException("Expected CSV string or object")
            }
            
            if (index < 0 || index >= csvData.rows.size) {
                throw FunctionArgumentException("Row index $index out of bounds")
            }
            
            UDM.Object(csvData.rows[index].mapValues { UDM.Scalar(it.value) })
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to get CSV row: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Get a specific cell value",
        minArgs = 3,
        maxArgs = 3,
        category = "CSV",
        parameters = [
            "csv: Csv value",
        "rowIndex: Rowindex value",
        "columnName: Columnname value"
        ],
        returns = "Result of the operation",
        example = "csvCell(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Get a specific cell value
     * 
     * @param args List containing: [csv, rowIndex, columnName]
     * @return UDM Scalar with cell value
     */
    fun csvCell(args: List<UDM>): UDM {
        if (args.size != 3) {
            throw FunctionArgumentException("csvCell expects 3 arguments (csv, rowIndex, columnName), got ${args.size}")
        }
        
        val csv = args[0]
        val rowIndex = args[1].asInt()
        val columnName = args[2].asString()
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                is UDM.Object -> parseCSVFromUDM(csv)
                else -> throw FunctionArgumentException("Expected CSV string or object")
            }
            
            if (columnName !in csvData.headers) {
                throw FunctionArgumentException("Column '$columnName' not found")
            }
            
            if (rowIndex < 0 || rowIndex >= csvData.rows.size) {
                throw FunctionArgumentException("Row index $rowIndex out of bounds")
            }
            
            UDM.Scalar(csvData.rows[rowIndex][columnName] ?: "")
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to get CSV cell: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Transpose CSV (swap rows and columns)",
        minArgs = 4,
        maxArgs = 4,
        category = "CSV",
        parameters = [
            "csv: Csv value",
        "columnName: Columnname value",
        "operator: Operator value",
        "filterValue: Filtervalue value"
        ],
        returns = "Result of the operation",
        example = "csvTranspose(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Transpose CSV (swap rows and columns)
     * 
     * @param args List containing: [csv]
     * @return UDM Scalar with transposed CSV string
     */
    fun csvTranspose(args: List<UDM>): UDM {
        requireArgs(args, 1, "csvTranspose")
        val csv = args[0]
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                else -> throw FunctionArgumentException("Expected CSV string")
            }
            
            val transposed = mutableListOf<List<String>>()
            
            // First row: field names become "field, 0, 1, 2..."
            transposed.add(listOf("field") + csvData.rows.indices.map { it.toString() })
            
            // Each original column becomes a row
            csvData.headers.forEach { header ->
                val newRow = mutableListOf(header)
                csvData.rows.forEach { row ->
                    newRow.add(row[header] ?: "")
                }
                transposed.add(newRow)
            }
            
            val result = transposed.joinToString("\n") { row ->
                row.joinToString(",")
            }
            
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to transpose CSV: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Filter CSV rows based on column value",
        minArgs = 4,
        maxArgs = 4,
        category = "CSV",
        parameters = [
            "csv: Csv value",
        "columnName: Columnname value",
        "operator: Operator value",
        "filterValue: Filtervalue value"
        ],
        returns = "New array with filtered elements",
        example = "csvFilter(...) => result",
        tags = ["csv", "filter"],
        since = "1.0"
    )
    /**
     * Filter CSV rows based on column value
     * 
     * @param args List containing: [csv, columnName, operator, value]
     * @return UDM Scalar with filtered CSV string
     */
    fun csvFilter(args: List<UDM>): UDM {
        if (args.size != 4) {
            throw FunctionArgumentException("csvFilter expects 4 arguments (csv, columnName, operator, value), got ${args.size}")
        }
        
        val csv = args[0]
        val columnName = args[1].asString()
        val operator = args[2].asString()
        val filterValue = args[3].asString()
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                else -> throw FunctionArgumentException("Expected CSV string")
            }
            
            if (columnName !in csvData.headers) {
                throw FunctionArgumentException("Column '$columnName' not found")
            }
            
            val filteredRows = csvData.rows.filter { row ->
                val cellValue = row[columnName] ?: ""
                when (operator.lowercase()) {
                    "eq", "equals", "=" -> cellValue == filterValue
                    "ne", "not_equals", "!=" -> cellValue != filterValue
                    "contains" -> cellValue.contains(filterValue)
                    "startswith" -> cellValue.startsWith(filterValue)
                    "endswith" -> cellValue.endsWith(filterValue)
                    "gt", ">" -> cellValue.toDoubleOrNull()?.let { it > (filterValue.toDoubleOrNull() ?: 0.0) } ?: false
                    "lt", "<" -> cellValue.toDoubleOrNull()?.let { it < (filterValue.toDoubleOrNull() ?: 0.0) } ?: false
                    "gte", ">=" -> cellValue.toDoubleOrNull()?.let { it >= (filterValue.toDoubleOrNull() ?: 0.0) } ?: false
                    "lte", "<=" -> cellValue.toDoubleOrNull()?.let { it <= (filterValue.toDoubleOrNull() ?: 0.0) } ?: false
                    else -> throw FunctionArgumentException("Unknown operator: $operator")
                }
            }
            
            val result = buildString {
                append(csvData.headers.joinToString(","))
                append("\n")
                filteredRows.forEach { row ->
                    append(csvData.headers.joinToString(",") { row[it] ?: "" })
                    append("\n")
                }
            }.trimEnd()
            
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to filter CSV: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Sort CSV by specified columns",
        minArgs = 3,
        maxArgs = 3,
        category = "CSV",
        parameters = [
            "csv: Csv value",
        "columnName: Columnname value"
        ],
        returns = "Result of the operation",
        example = "csvSort(...) => result",
        tags = ["csv", "sort"],
        since = "1.0"
    )
    /**
     * Sort CSV by specified columns
     * 
     * @param args List containing: [csv, columnName, ascending?]
     * @return UDM Scalar with sorted CSV string
     */
    fun csvSort(args: List<UDM>): UDM {
        if (args.size < 2 || args.size > 3) {
            throw FunctionArgumentException("csvSort expects 2 or 3 arguments (csv, columnName, ascending?), got ${args.size}")
        }
        
        val csv = args[0]
        val columnName = args[1].asString()
        val ascending = if (args.size > 2) args[2].asBoolean() else true
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                else -> throw FunctionArgumentException("Expected CSV string")
            }
            
            if (columnName !in csvData.headers) {
                throw FunctionArgumentException("Column '$columnName' not found")
            }
            
            val sortedRows = csvData.rows.sortedWith { row1, row2 ->
                val val1 = row1[columnName] ?: ""
                val val2 = row2[columnName] ?: ""
                
                val comparison = val1.compareTo(val2)
                if (ascending) comparison else -comparison
            }
            
            val result = buildString {
                append(csvData.headers.joinToString(","))
                append("\n")
                sortedRows.forEach { row ->
                    append(csvData.headers.joinToString(",") { row[it] ?: "" })
                    append("\n")
                }
            }.trimEnd()
            
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to sort CSV: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Add a new column to CSV with computed values",
        minArgs = 3,
        maxArgs = 3,
        category = "CSV",
        parameters = [
            "array: Input array to process",
        "columnName: Columnname value",
        "defaultValue: Defaultvalue value"
        ],
        returns = "Result of the operation",
        example = "csvAddColumn(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Add a new column to CSV with computed values
     * 
     * @param args List containing: [csv, columnName, defaultValue]
     * @return UDM Scalar with CSV containing new column
     */
    fun csvAddColumn(args: List<UDM>): UDM {
        if (args.size != 3) {
            throw FunctionArgumentException("csvAddColumn expects 3 arguments (csv, columnName, defaultValue), got ${args.size}")
        }
        
        val csv = args[0]
        val columnName = args[1].asString()
        val defaultValue = args[2].asString()
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                else -> throw FunctionArgumentException("Expected CSV string")
            }
            
            if (columnName in csvData.headers) {
                throw FunctionArgumentException("Column '$columnName' already exists")
            }
            
            val newHeaders = csvData.headers + columnName
            val newRows = csvData.rows.map { row ->
                row + (columnName to defaultValue)
            }
            
            val result = buildString {
                append(newHeaders.joinToString(","))
                append("\n")
                newRows.forEach { row ->
                    append(newHeaders.joinToString(",") { row[it] ?: "" })
                    append("\n")
                }
            }.trimEnd()
            
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to add CSV column: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Remove columns from CSV",
        minArgs = 1,
        maxArgs = 1,
        category = "CSV",
        parameters = [
            "array: Input array to process",
        "columnsToRemove: Columnstoremove value"
        ],
        returns = "Result of the operation",
        example = "csvRemoveColumns(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Remove columns from CSV
     * 
     * @param args List containing: [csv, columnNames]
     * @return UDM Scalar with CSV without specified columns
     */
    fun csvRemoveColumns(args: List<UDM>): UDM {
        requireArgs(args, 2, "csvRemoveColumns")
        val csv = args[0]
        val columnsToRemove = args[1].asStringArray()
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                else -> throw FunctionArgumentException("Expected CSV string")
            }
            
            val newHeaders = csvData.headers.filter { it !in columnsToRemove }
            
            if (newHeaders.isEmpty()) {
                throw FunctionArgumentException("Cannot remove all columns")
            }
            
            val result = buildString {
                append(newHeaders.joinToString(","))
                append("\n")
                csvData.rows.forEach { row ->
                    append(newHeaders.joinToString(",") { row[it] ?: "" })
                    append("\n")
                }
            }.trimEnd()
            
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to remove CSV columns: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Select/project specific columns from CSV",
        minArgs = 2,
        maxArgs = 2,
        category = "CSV",
        parameters = [
            "array: Input array to process",
        "columnsToSelect: Columnstoselect value"
        ],
        returns = "Result of the operation",
        example = "csvSelectColumns(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Select/project specific columns from CSV
     * 
     * @param args List containing: [csv, columnNames]
     * @return UDM Scalar with CSV containing only specified columns
     */
    fun csvSelectColumns(args: List<UDM>): UDM {
        requireArgs(args, 2, "csvSelectColumns")
        val csv = args[0]
        val columnsToSelect = args[1].asStringArray()
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                else -> throw FunctionArgumentException("Expected CSV string")
            }
            
            // Validate all columns exist
            columnsToSelect.forEach { col ->
                if (col !in csvData.headers) {
                    throw FunctionArgumentException("Column '$col' not found")
                }
            }
            
            val result = buildString {
                append(columnsToSelect.joinToString(","))
                append("\n")
                csvData.rows.forEach { row ->
                    append(columnsToSelect.joinToString(",") { row[it] ?: "" })
                    append("\n")
                }
            }.trimEnd()
            
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to select CSV columns: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Calculate summary statistics for CSV columns",
        minArgs = 2,
        maxArgs = 2,
        category = "CSV",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "csvSummarize(...) => result",
        tags = ["csv"],
        since = "1.0"
    )
    /**
     * Calculate summary statistics for CSV columns
     * 
     * @param args List containing: [csv, columnNames?]
     * @return UDM Object with summary statistics
     */
    fun csvSummarize(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException("csvSummarize expects 1 or 2 arguments (csv, columnNames?), got ${args.size}")
        }
        
        val csv = args[0]
        val columns = if (args.size > 1) args[1].asStringArray() else emptyList()
        
        return try {
            val csvData = when (csv) {
                is UDM.Scalar -> parseCSVString(csv.asString())
                else -> throw FunctionArgumentException("Expected CSV string")
            }
            
            val targetColumns = if (columns.isEmpty()) {
                // Auto-detect numeric columns
                csvData.headers.filter { header ->
                    csvData.rows.any { row ->
                        row[header]?.toDoubleOrNull() != null
                    }
                }
            } else {
                columns
            }
            
            val summary = mutableMapOf<String, UDM>()
            
            targetColumns.forEach { col ->
                if (col !in csvData.headers) {
                    throw FunctionArgumentException("Column '$col' not found")
                }
                
                val values = csvData.rows.mapNotNull { row ->
                    row[col]?.toDoubleOrNull()
                }
                
                if (values.isNotEmpty()) {
                    summary[col] = UDM.Object(mapOf(
                        "count" to UDM.Scalar(values.size.toDouble()),
                        "sum" to UDM.Scalar(values.sum()),
                        "avg" to UDM.Scalar(values.average()),
                        "min" to UDM.Scalar(values.minOrNull() ?: 0.0),
                        "max" to UDM.Scalar(values.maxOrNull() ?: 0.0)
                    ))
                }
            }
            
            UDM.Object(summary)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to summarize CSV: ${e.message}")
        }
    }

    // Helper functions
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException(
            "Expected string value, but got ${getTypeDescription(this)}. " +
            "Hint: Use toString() to convert values to strings."
        )
    }

    private fun UDM.asInt(): Int = when (this) {
        is UDM.Scalar -> (value as? Number)?.toInt() ?: throw FunctionArgumentException(
            "Expected integer value, but got ${getTypeDescription(this)}. " +
            "Hint: Ensure the value is a valid integer."
        )
        else -> throw FunctionArgumentException(
            "Expected integer value, but got ${getTypeDescription(this)}. " +
            "Hint: Use toNumber() to convert values to numbers."
        )
    }

    private fun UDM.asBoolean(): Boolean = when (this) {
        is UDM.Scalar -> value as? Boolean ?: throw FunctionArgumentException(
            "Expected boolean value, but got ${getTypeDescription(this)}. " +
            "Hint: Ensure the value is true or false."
        )
        else -> throw FunctionArgumentException(
            "Expected boolean value, but got ${getTypeDescription(this)}. " +
            "Hint: Provide a boolean value (true or false)."
        )
    }

    private fun UDM.asStringArray(): List<String> = when (this) {
        is UDM.Array -> elements.map {
            when (it) {
                is UDM.Scalar -> it.value?.toString() ?: ""
                else -> throw FunctionArgumentException(
                    "Expected array of strings, but array contains ${getTypeDescription(it)}. " +
                    "Hint: Ensure all array elements are strings."
                )
            }
        }
        else -> throw FunctionArgumentException(
            "Expected array value, but got ${getTypeDescription(this)}. " +
            "Hint: Provide an array of strings."
        )
    }

    private fun getTypeDescription(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    is String -> "string"
                    is Number -> "number"
                    is Boolean -> "boolean"
                    null -> "null"
                    else -> value.javaClass.simpleName
                }
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.Binary -> "binary"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }

    // Internal CSV data structure
    private data class CSVData(
        val headers: List<String>,
        val rows: List<Map<String, String>>
    )

    // Simple CSV parsing (basic implementation)
    private fun parseCSVString(csvString: String, delimiter: String = ","): CSVData {
        val lines = csvString.trim().split("\n")
        if (lines.isEmpty()) {
            return CSVData(emptyList(), emptyList())
        }
        
        val headers = lines[0].split(delimiter).map { it.trim() }
        val rows = lines.drop(1).map { line ->
            val values = line.split(delimiter).map { it.trim() }
            headers.zip(values).toMap()
        }
        
        return CSVData(headers, rows)
    }

    // Parse CSV from UDM structure
    private fun parseCSVFromUDM(udm: UDM.Object): CSVData {
        val headers = (udm.properties["headers"] as? UDM.Array)?.elements
            ?.map { (it as? UDM.Scalar)?.value?.toString() ?: "" }
            ?: throw FunctionArgumentException("Invalid CSV structure: missing headers")
        
        val rows = (udm.properties["rows"] as? UDM.Array)?.elements
            ?.map { row ->
                (row as? UDM.Object)?.properties?.mapValues { (_, v) ->
                    (v as? UDM.Scalar)?.value?.toString() ?: ""
                } ?: emptyMap()
            }
            ?: throw FunctionArgumentException("Invalid CSV structure: missing rows")
        
        return CSVData(headers, rows)
    }
}
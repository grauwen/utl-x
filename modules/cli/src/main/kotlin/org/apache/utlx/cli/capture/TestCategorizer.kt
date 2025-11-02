// modules/cli/src/main/kotlin/org/apache/utlx/cli/capture/TestCategorizer.kt
package org.apache.utlx.cli.capture

/**
 * Categorizes tests based on transformation content
 */
object TestCategorizer {

    // Map of function names to their categories
    private val functionCategories = mapOf(
        // String functions
        "upper" to "stdlib/string",
        "lower" to "stdlib/string",
        "trim" to "stdlib/string",
        "substring" to "stdlib/string",
        "concat" to "stdlib/string",
        "split" to "stdlib/string",
        "join" to "stdlib/string",
        "replace" to "stdlib/string",
        "contains" to "stdlib/string",
        "startsWith" to "stdlib/string",
        "endsWith" to "stdlib/string",
        "length" to "stdlib/string",

        // Array functions
        "map" to "stdlib/array",
        "filter" to "stdlib/array",
        "reduce" to "stdlib/array",
        "sum" to "stdlib/array",
        "avg" to "stdlib/array",
        "min" to "stdlib/array",
        "max" to "stdlib/array",
        "sort" to "stdlib/array",
        "sortBy" to "stdlib/array",
        "reverse" to "stdlib/array",
        "first" to "stdlib/array",
        "last" to "stdlib/array",
        "take" to "stdlib/array",
        "drop" to "stdlib/array",
        "find" to "stdlib/array",
        "findIndex" to "stdlib/array",

        // Math functions
        "abs" to "stdlib/math",
        "round" to "stdlib/math",
        "ceil" to "stdlib/math",
        "floor" to "stdlib/math",
        "pow" to "stdlib/math",
        "sqrt" to "stdlib/math",
        "distance" to "stdlib/math",

        // Date functions
        "now" to "stdlib/datetime",
        "parseDate" to "stdlib/datetime",
        "formatDate" to "stdlib/datetime",
        "addDays" to "stdlib/datetime",
        "diffDays" to "stdlib/datetime",

        // Type functions
        "typeOf" to "stdlib/type",
        "isString" to "stdlib/type",
        "isNumber" to "stdlib/type",
        "isArray" to "stdlib/type",
        "isObject" to "stdlib/type",
        "isBoolean" to "stdlib/type",
        "isNull" to "stdlib/type",

        // Serialization functions
        "parseJson" to "stdlib/serialization",
        "renderJson" to "stdlib/serialization",
        "parseXml" to "stdlib/serialization",
        "renderXml" to "stdlib/serialization",
        "parseYaml" to "stdlib/serialization",
        "renderYaml" to "stdlib/serialization",
        "parseCsv" to "stdlib/serialization",
        "renderCsv" to "stdlib/serialization",

        // Encoding functions
        "base64" to "stdlib/encoding",
        "base64Decode" to "stdlib/encoding",
        "urlEncode" to "stdlib/encoding",
        "urlDecode" to "stdlib/encoding",
        "md5" to "stdlib/crypto",
        "sha1" to "stdlib/crypto",
        "sha256" to "stdlib/crypto"
    )

    /**
     * Extract function names from transformation script
     */
    fun extractFunctions(transformation: String): List<String> {
        val functionPattern = Regex("""(\w+)\s*\(""")
        return functionPattern.findAll(transformation)
            .map { it.groupValues[1] }
            .filter { it in functionCategories.keys }
            .distinct()
            .toList()
    }

    /**
     * Determine test category based on transformation content
     * Primary: format-based categorization (input/output formats)
     * Fallback: function-based categorization
     */
    fun categorize(transformation: String, inputFormat: String, outputFormat: String): String {
        // Primary: categorize by format transformation
        val formatCategory = when {
            inputFormat == "xml" && outputFormat == "json" -> "xml-to-json"
            inputFormat == "json" && outputFormat == "xml" -> "json-to-xml"
            inputFormat == "csv" && outputFormat == "json" -> "csv-to-json"
            inputFormat == "json" && outputFormat == "csv" -> "json-to-csv"
            inputFormat == "xml" && outputFormat == "csv" -> "xml-to-csv"
            inputFormat == outputFormat -> inputFormat  // e.g. "json", "xml", "csv"
            else -> null
        }

        if (formatCategory != null) {
            return formatCategory
        }

        // Fallback: categorize by function (strip "stdlib/" prefix - it's redundant)
        val functions = extractFunctions(transformation)
        if (functions.isNotEmpty()) {
            val primaryFunction = functions.first()
            val category = functionCategories[primaryFunction]
            return category?.removePrefix("stdlib/") ?: "uncategorized"
        }

        return "uncategorized"
    }

    /**
     * Get primary function name for test naming
     */
    fun getPrimaryFunction(transformation: String): String? {
        return extractFunctions(transformation).firstOrNull()
    }
}

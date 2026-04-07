package org.apache.utlx.core.interpreter

import org.apache.utlx.core.udm.UDM

/**
 * Extracts metadata from parsed input data for use in error enhancement.
 *
 * This metadata helps the InterpreterErrorEnhancer provide smart, context-aware
 * error messages by analyzing the actual structure of input data.
 *
 * Example: When a user references an undefined field "Departmant" (typo),
 * the enhancer can suggest "Department" because it knows the actual field names
 * from the CSV headers or JSON keys.
 */
object InputMetadataExtractor {

    /**
     * Extract metadata from a UDM value
     *
     * @param name The name of the input (e.g., "employees", "orders")
     * @param udm The parsed UDM structure
     * @param format The format of the original input ("csv", "json", "xml", etc.)
     * @return InputMetadata with extracted field names and statistics
     */
    fun extract(name: String, udm: UDM, format: String): InterpreterErrorEnhancer.InputMetadata {
        val fields = extractFieldNames(udm)
        val recordCount = extractRecordCount(udm)
        val sampleValue = extractSampleValue(udm)

        return InterpreterErrorEnhancer.InputMetadata(
            name = name,
            format = format,
            fields = fields,
            sampleValue = sampleValue,
            recordCount = recordCount
        )
    }

    /**
     * Extract field names from UDM structure
     *
     * For CSV with headers: Returns header names
     * For JSON objects: Returns top-level keys
     * For JSON array of objects: Returns keys from first object
     * For XML: Returns element names (future enhancement)
     */
    private fun extractFieldNames(udm: UDM): List<String>? {
        return when (udm) {
            is UDM.Array -> {
                // Array of objects - extract keys from first object
                val firstElement = udm.elements.firstOrNull()
                if (firstElement is UDM.Object) {
                    firstElement.properties.keys.toList()
                } else {
                    null
                }
            }
            is UDM.Object -> {
                // Single object - return its keys
                udm.properties.keys.toList()
            }
            else -> null
        }
    }

    /**
     * Extract record count for arrays
     */
    private fun extractRecordCount(udm: UDM): Int? {
        return when (udm) {
            is UDM.Array -> udm.elements.size
            else -> null
        }
    }

    /**
     * Extract a sample value for error messages (first element or the value itself)
     */
    private fun extractSampleValue(udm: UDM): RuntimeValue? {
        return try {
            when (udm) {
                is UDM.Array -> {
                    val first = udm.elements.firstOrNull() ?: return null
                    RuntimeValue.UDMValue(first)
                }
                else -> RuntimeValue.UDMValue(udm)
            }
        } catch (e: Exception) {
            null // Silently fail if conversion not possible
        }
    }

    /**
     * Extract metadata from multiple named inputs
     *
     * @param namedInputs Map of input name to UDM
     * @param formats Map of input name to format (optional)
     * @return Map of input name to InputMetadata
     */
    fun extractAll(
        namedInputs: Map<String, UDM>,
        formats: Map<String, String> = emptyMap()
    ): Map<String, InterpreterErrorEnhancer.InputMetadata> {
        return namedInputs.mapValues { (name, udm) ->
            val format = formats[name] ?: "unknown"
            extract(name, udm, format)
        }
    }
}

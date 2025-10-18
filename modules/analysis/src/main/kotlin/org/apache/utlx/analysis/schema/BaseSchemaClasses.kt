// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/BaseSchemaClasses.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.TypeDefinition

/**
 * Exception thrown when schema parsing fails
 */
class SchemaParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Base interface for input schema parsers
 */
interface InputSchemaParser {
    fun parse(schema: String, format: SchemaFormat): TypeDefinition
}

/**
 * Base interface for output schema generators
 */
interface OutputSchemaGenerator {
    fun generate(type: TypeDefinition, options: GeneratorOptions): String
}

/**
 * Supported schema formats
 */
enum class SchemaFormat {
    XSD,
    JSON_SCHEMA,
    CSV_SCHEMA,
    YAML_SCHEMA
}

/**
 * Options for schema generation
 */
data class GeneratorOptions(
    val pretty: Boolean = true,
    val includeComments: Boolean = true,
    val includeExamples: Boolean = false,
    val strictMode: Boolean = true,
    val targetVersion: String? = null,  // e.g., "draft-07" for JSON Schema
    val namespace: String? = null,       // for XSD
    val rootElementName: String? = null
)

/**
 * Validation result for transformations
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
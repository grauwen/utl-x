// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/analysis/DocumentAnalyzer.kt
package org.apache.utlx.daemon.analysis

import org.apache.utlx.analysis.types.*
import org.slf4j.LoggerFactory

/**
 * Document Analyzer for LSP Daemon
 *
 * Analyzes UTL-X documents to extract type information:
 * - Parses input type declarations
 * - Creates type environments for completion/hover
 * - Handles both inline type declarations and schema references
 */
class DocumentAnalyzer {

    private val logger = LoggerFactory.getLogger(DocumentAnalyzer::class.java)

    /**
     * Analyze a document and create a type environment
     *
     * Extracts the input type from declarations like:
     * ```
     * input: { name: string, age: number }
     * output: input.name
     * ```
     *
     * @param text Document content
     * @return TypeContext with input binding, or null if no input type found
     */
    fun analyzeDocument(text: String): TypeContext? {
        try {
            // Extract input type declaration
            val inputType = extractInputType(text)

            if (inputType == null) {
                logger.debug("No input type declaration found in document")
                return null
            }

            // Create type context with input binding
            val context = TypeContextBuilder.standard(inputType)

            logger.debug("Created type context with input type: ${inputType::class.simpleName}")

            return context

        } catch (e: Exception) {
            logger.error("Error analyzing document", e)
            return null
        }
    }

    /**
     * Extract input type from document
     *
     * Supports formats:
     * - `input: { name: string, age: number }`
     * - `input: string`
     * - `input: [ number ]`
     */
    private fun extractInputType(text: String): TypeDefinition? {
        // Look for "input:" declaration
        val inputMatch = Regex("""input\s*:\s*(.+)""", RegexOption.MULTILINE)
            .find(text) ?: return null

        val typeDeclaration = inputMatch.groupValues[1]
            .substringBefore("output")  // Stop at output declaration
            .substringBefore("//")       // Stop at comments
            .trim()

        logger.debug("Found input type declaration: $typeDeclaration")

        // Parse the type declaration
        return parseTypeDeclaration(typeDeclaration)
    }

    /**
     * Parse a type declaration string into a TypeDefinition
     *
     * Supports:
     * - Object types: `{ name: string, age: number }`
     * - Array types: `[ string ]`
     * - Scalar types: `string`, `number`, `boolean`
     * - Nested types: `{ user: { name: string } }`
     */
    private fun parseTypeDeclaration(decl: String): TypeDefinition? {
        val trimmed = decl.trim()

        return when {
            // Object type: { ... }
            trimmed.startsWith("{") && trimmed.endsWith("}") -> {
                parseObjectType(trimmed.substring(1, trimmed.length - 1))
            }

            // Array type: [ type ]
            trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                val elementTypeDecl = trimmed.substring(1, trimmed.length - 1).trim()
                val elementType = parseTypeDeclaration(elementTypeDecl)
                    ?: TypeDefinition.Unknown
                TypeDefinition.Array(elementType)
            }

            // Scalar types
            trimmed.equals("string", ignoreCase = true) -> {
                TypeDefinition.Scalar(ScalarKind.STRING)
            }
            trimmed.equals("number", ignoreCase = true) -> {
                TypeDefinition.Scalar(ScalarKind.NUMBER)
            }
            trimmed.equals("integer", ignoreCase = true) || trimmed.equals("int", ignoreCase = true) -> {
                TypeDefinition.Scalar(ScalarKind.INTEGER)
            }
            trimmed.equals("boolean", ignoreCase = true) || trimmed.equals("bool", ignoreCase = true) -> {
                TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            }
            trimmed.equals("date", ignoreCase = true) -> {
                TypeDefinition.Scalar(ScalarKind.DATE)
            }
            trimmed.equals("datetime", ignoreCase = true) -> {
                TypeDefinition.Scalar(ScalarKind.DATETIME)
            }

            else -> {
                logger.warn("Unknown type declaration: $trimmed")
                TypeDefinition.Unknown
            }
        }
    }

    /**
     * Parse object type properties
     *
     * Example: `name: string, age: number, address: { city: string }`
     */
    private fun parseObjectType(propertiesDecl: String): TypeDefinition {
        val properties = mutableMapOf<String, PropertyType>()

        // Split by commas, but respect nested braces
        val propertyDecls = splitProperties(propertiesDecl)

        for (propDecl in propertyDecls) {
            val parts = propDecl.split(":", limit = 2)
            if (parts.size != 2) {
                logger.warn("Invalid property declaration: $propDecl")
                continue
            }

            val propName = parts[0].trim()
            val propTypeDecl = parts[1].trim()

            val propType = parseTypeDeclaration(propTypeDecl)
                ?: TypeDefinition.Unknown

            properties[propName] = PropertyType(propType, nullable = false)
        }

        return TypeDefinition.Object(
            properties = properties,
            required = properties.keys // All declared properties are required by default
        )
    }

    /**
     * Split properties by comma, respecting nested braces and brackets
     *
     * Example: "name: string, address: { city: string, zip: string }, age: number"
     * Returns: ["name: string", "address: { city: string, zip: string }", "age: number"]
     */
    private fun splitProperties(text: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var braceDepth = 0
        var bracketDepth = 0

        for (char in text) {
            when (char) {
                '{' -> {
                    braceDepth++
                    current.append(char)
                }
                '}' -> {
                    braceDepth--
                    current.append(char)
                }
                '[' -> {
                    bracketDepth++
                    current.append(char)
                }
                ']' -> {
                    bracketDepth--
                    current.append(char)
                }
                ',' -> {
                    if (braceDepth == 0 && bracketDepth == 0) {
                        // Top-level comma - split here
                        val prop = current.toString().trim()
                        if (prop.isNotEmpty()) {
                            result.add(prop)
                        }
                        current = StringBuilder()
                    } else {
                        // Nested comma - include in current
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }

        // Add last property
        val prop = current.toString().trim()
        if (prop.isNotEmpty()) {
            result.add(prop)
        }

        return result
    }
}

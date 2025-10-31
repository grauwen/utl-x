// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/diagnostics/DiagnosticsAnalyzer.kt
package org.apache.utlx.daemon.diagnostics

import org.apache.utlx.analysis.types.*
import org.apache.utlx.daemon.completion.Position
import org.apache.utlx.daemon.hover.Range
import org.slf4j.LoggerFactory

/**
 * Diagnostics Analyzer
 *
 * Analyzes UTL-X documents for errors and warnings.
 *
 * Phase 2 implementation focuses on basic path validation:
 * - Undefined variable references
 * - Invalid property access
 * - Type mismatches (future)
 */
class DiagnosticsAnalyzer {

    private val logger = LoggerFactory.getLogger(DiagnosticsAnalyzer::class.java)

    /**
     * Analyze a document and return diagnostics
     *
     * @param text Document text
     * @param typeContext Type environment for the document
     * @return List of diagnostics (errors, warnings, etc.)
     */
    fun analyze(text: String, typeContext: TypeContext): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        // Extract all path references from the document
        val pathReferences = extractPathReferences(text)

        // Validate each path
        for (pathRef in pathReferences) {
            val type = resolvePath(pathRef.path, typeContext)

            if (type == null) {
                // Path doesn't resolve to a valid type
                diagnostics.add(
                    Diagnostic(
                        range = pathRef.range,
                        severity = DiagnosticSeverity.ERROR,
                        code = "undefined-path",
                        message = "Cannot resolve path '${pathRef.path}'",
                        source = "utlx"
                    )
                )
            }
        }

        logger.debug("Found ${diagnostics.size} diagnostic(s)")
        return diagnostics
    }

    /**
     * Extract all path references from document text
     *
     * Simple implementation that looks for input.* patterns
     */
    private fun extractPathReferences(text: String): List<PathReference> {
        val references = mutableListOf<PathReference>()
        val lines = text.lines()

        for ((lineIndex, line) in lines.withIndex()) {
            // Find all "input" references and their paths
            var index = 0
            while (index < line.length) {
                // Look for path start
                if (line.startsWith("input", index) ||
                    (index > 0 && line[index - 1] == '$' && line[index].isLetter())) {

                    val start = if (line.startsWith("input", index)) {
                        index
                    } else {
                        index - 1 // Include the $
                    }

                    // Extract the full path
                    var end = start
                    while (end < line.length) {
                        val ch = line[end]
                        if (ch.isLetterOrDigit() || ch == '.' || ch == '@' || ch == '_' || ch == '$') {
                            end++
                        } else {
                            break
                        }
                    }

                    val path = line.substring(start, end)

                    // Only include if it looks like a valid path
                    if (isValidPath(path) && path.contains(".")) {
                        references.add(
                            PathReference(
                                path = path,
                                range = Range(
                                    start = Position(lineIndex, start),
                                    end = Position(lineIndex, end)
                                )
                            )
                        )
                    }

                    index = end
                } else {
                    index++
                }
            }
        }

        return references
    }

    /**
     * Check if a string looks like a valid path
     */
    private fun isValidPath(text: String): Boolean {
        return text.startsWith("input") ||
                text.startsWith("$") ||
                text.firstOrNull()?.isLetter() == true
    }

    /**
     * Resolve a path to its type
     */
    private fun resolvePath(path: String, typeContext: TypeContext): TypeDefinition? {
        val parts = path.split(".")

        // Start with the first part (usually "input")
        var currentType = typeContext.lookup(parts[0]) ?: return null

        // Traverse the rest of the path
        for (i in 1 until parts.size) {
            val part = parts[i]

            // Skip attribute markers
            if (part.startsWith("@")) {
                continue
            }

            currentType = when (currentType) {
                is TypeDefinition.Object -> {
                    // Access object property
                    currentType.properties[part]?.type
                }
                is TypeDefinition.Array -> {
                    // Array access - if part is a property name, access element type's property
                    when (val elementType = currentType.elementType) {
                        is TypeDefinition.Object -> elementType.properties[part]?.type
                        else -> null
                    }
                }
                is TypeDefinition.Union -> {
                    // For unions, try to find the property in any member
                    currentType.types.firstNotNullOfOrNull { memberType ->
                        when (memberType) {
                            is TypeDefinition.Object -> memberType.properties[part]?.type
                            is TypeDefinition.Array -> {
                                when (val elemType = memberType.elementType) {
                                    is TypeDefinition.Object -> elemType.properties[part]?.type
                                    else -> null
                                }
                            }
                            else -> null
                        }
                    }
                }
                else -> null
            } ?: return null
        }

        return currentType
    }

    /**
     * Path reference with its location
     */
    private data class PathReference(
        val path: String,
        val range: Range
    )
}

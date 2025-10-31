// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/hover/HoverService.kt
package org.apache.utlx.daemon.hover

import org.apache.utlx.analysis.types.*
import org.apache.utlx.daemon.completion.Position
import org.apache.utlx.daemon.state.StateManager
import org.slf4j.LoggerFactory

/**
 * Hover Service
 *
 * Provides type information when hovering over code elements.
 */
class HoverService(
    private val stateManager: StateManager
) {
    private val logger = LoggerFactory.getLogger(HoverService::class.java)

    /**
     * Handle hover request
     *
     * @param params Hover parameters from LSP client
     * @return Hover information or null if no information available
     */
    fun getHover(params: HoverParams): Hover? {
        val uri = params.textDocument.uri
        val position = params.position

        logger.debug("Hover request for $uri at ${position.line}:${position.character}")

        // Get document text
        val text = stateManager.getDocumentText(uri)
        if (text == null) {
            logger.warn("Document not found: $uri")
            return null
        }

        // Get type environment for this document
        val typeEnv = stateManager.getTypeEnvironment(uri)
        if (typeEnv == null) {
            logger.warn("No type environment for document: $uri")
            return null
        }

        // Extract the path at cursor position
        val pathInfo = extractPathAtPosition(text, position)
        if (pathInfo == null) {
            logger.debug("No path found at position")
            return null
        }

        logger.debug("Extracted path: '${pathInfo.path}'")

        // Resolve the type for this path
        val type = resolvePath(pathInfo.path, typeEnv)
        if (type == null) {
            logger.debug("No type found for path: ${pathInfo.path}")
            return null
        }

        logger.debug("Found type: ${type::class.simpleName}")

        // Format hover content
        val content = formatHoverContent(pathInfo.path, type)

        return Hover(
            contents = MarkupContent(
                kind = MarkupKind.MARKDOWN,
                value = content
            ),
            range = pathInfo.range
        )
    }

    /**
     * Extract path and its range at the given position
     */
    private fun extractPathAtPosition(text: String, position: Position): PathInfo? {
        val lines = text.lines()
        if (position.line >= lines.size) {
            return null
        }

        val line = lines[position.line]
        if (position.character > line.length) {
            return null
        }

        // Find the start and end of the path expression
        var start = position.character
        var end = position.character

        // Search backwards for path start
        while (start > 0) {
            val ch = line[start - 1]
            if (ch.isLetterOrDigit() || ch == '.' || ch == '@' || ch == '_') {
                start--
            } else if (ch == '$') {
                start--
                break
            } else {
                break
            }
        }

        // Search forwards for path end
        while (end < line.length) {
            val ch = line[end]
            if (ch.isLetterOrDigit() || ch == '.' || ch == '@' || ch == '_') {
                end++
            } else {
                break
            }
        }

        if (start >= end) {
            return null
        }

        val path = line.substring(start, end)

        // Validate that it looks like a path
        if (!isValidPath(path)) {
            return null
        }

        return PathInfo(
            path = path,
            range = Range(
                start = Position(position.line, start),
                end = Position(position.line, end)
            )
        )
    }

    /**
     * Check if a string looks like a valid path
     */
    private fun isValidPath(text: String): Boolean {
        return text.startsWith("input") ||
                text.startsWith("$") ||
                text.firstOrNull()?.isLetter() == true ||
                text.startsWith("@")
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
     * Format hover content as markdown
     */
    private fun formatHoverContent(path: String, type: TypeDefinition): String {
        return buildString {
            appendLine("**Path**: `$path`")
            appendLine()
            appendLine("**Type**: `${formatTypeDetail(type)}`")
            appendLine()

            // Add additional type information
            when (type) {
                is TypeDefinition.Scalar -> {
                    appendLine("*Scalar type*")
                    if (type.constraints.isNotEmpty()) {
                        appendLine()
                        appendLine("**Constraints**:")
                        type.constraints.forEach { constraint ->
                            appendLine("- ${constraint.kind}: `${constraint.value}`")
                        }
                    }
                }
                is TypeDefinition.Object -> {
                    appendLine("*Object type with ${type.properties.size} properties*")
                    if (type.required.isNotEmpty()) {
                        appendLine()
                        appendLine("**Required fields**: ${type.required.joinToString(", ") { "`$it`" }}")
                    }
                    if (type.properties.isNotEmpty()) {
                        appendLine()
                        appendLine("**Properties**:")
                        type.properties.entries.take(10).forEach { (name, propType) ->
                            val required = if (name in type.required) " *(required)*" else ""
                            val nullable = if (propType.nullable) "?" else ""
                            appendLine("- `$name$nullable`: ${formatTypeDetail(propType.type)}$required")
                        }
                        if (type.properties.size > 10) {
                            appendLine("- *... and ${type.properties.size - 10} more*")
                        }
                    }
                }
                is TypeDefinition.Array -> {
                    appendLine("*Array type*")
                    appendLine()
                    appendLine("**Element type**: `${formatTypeDetail(type.elementType)}`")
                    if (type.minItems != null) {
                        appendLine("**Min items**: ${type.minItems}")
                    }
                    if (type.maxItems != null) {
                        appendLine("**Max items**: ${type.maxItems}")
                    }
                }
                is TypeDefinition.Union -> {
                    appendLine("*Union type (one of)*")
                    appendLine()
                    appendLine("**Possible types**:")
                    type.types.forEach { memberType ->
                        appendLine("- `${formatTypeDetail(memberType)}`")
                    }
                }
                is TypeDefinition.Any -> {
                    appendLine("*Any type (no constraints)*")
                }
                is TypeDefinition.Unknown -> {
                    appendLine("*Unknown type (not yet determined)*")
                }
                is TypeDefinition.Never -> {
                    appendLine("*Never type (impossible/error)*")
                }
            }
        }
    }

    /**
     * Format type information for display
     */
    private fun formatTypeDetail(type: TypeDefinition): String {
        return when (type) {
            is TypeDefinition.Scalar -> when (type.kind) {
                ScalarKind.STRING -> "String"
                ScalarKind.NUMBER -> "Number"
                ScalarKind.INTEGER -> "Integer"
                ScalarKind.BOOLEAN -> "Boolean"
                ScalarKind.NULL -> "Null"
                ScalarKind.DATE -> "Date"
                ScalarKind.DATETIME -> "DateTime"
                ScalarKind.TIME -> "Time"
                ScalarKind.DURATION -> "Duration"
                ScalarKind.BINARY -> "Binary"
            }
            is TypeDefinition.Array -> "Array<${formatTypeDetail(type.elementType)}>"
            is TypeDefinition.Object -> "Object"
            is TypeDefinition.Union -> type.types.joinToString(" | ") { formatTypeDetail(it) }
            is TypeDefinition.Any -> "Any"
            is TypeDefinition.Unknown -> "Unknown"
            is TypeDefinition.Never -> "Never"
        }
    }

    /**
     * Path information with its text range
     */
    private data class PathInfo(
        val path: String,
        val range: Range
    )
}

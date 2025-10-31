// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/completion/PathCompleter.kt
package org.apache.utlx.daemon.completion

import org.apache.utlx.analysis.types.TypeContext
import org.apache.utlx.analysis.types.TypeDefinition
import org.apache.utlx.analysis.types.ScalarKind
import org.slf4j.LoggerFactory

/**
 * Path Completion Service
 *
 * Provides autocomplete suggestions for property access paths based on type information.
 *
 * Example:
 * ```
 * input.Order.Items.  ← User types this
 *                 ↑
 * PathCompleter suggests: Item, @count, etc.
 * ```
 */
class PathCompleter(
    private val typeContext: TypeContext
) {
    private val logger = LoggerFactory.getLogger(PathCompleter::class.java)

    /**
     * Get completion suggestions for a partial path
     *
     * @param path Partial path (e.g., "input.Order.Items.")
     * @return List of completion items
     */
    fun complete(path: String): List<CompletionItem> {
        logger.debug("Completing path: $path")

        // Handle empty path - suggest root
        if (path.isBlank()) {
            return listOf(
                CompletionItem(
                    label = "input",
                    kind = CompletionItemKind.VARIABLE,
                    detail = "Input document",
                    documentation = "The root input document"
                )
            )
        }

        // Parse path into parts
        val parts = path.split(".")
        val lastPart = parts.lastOrNull() ?: ""

        // Get the prefix path (everything except the last incomplete part)
        val prefixPath = if (path.endsWith(".")) {
            // User typed "input.Order." - complete from "input.Order"
            path.dropLast(1)
        } else {
            // User typed "input.Order.It" - complete from "input.Order" filtering "It"
            parts.dropLast(1).joinToString(".")
        }

        logger.debug("Prefix path: '$prefixPath', partial: '$lastPart'")

        // Look up the type at the prefix path
        val type = if (prefixPath.isNotEmpty()) {
            resolvePath(prefixPath)
        } else {
            // At root level - suggest "input"
            null
        }

        if (type == null) {
            logger.debug("No type found for prefix path: $prefixPath")
            return emptyList()
        }

        logger.debug("Found type: ${type::class.simpleName}")

        // Get completions based on type
        return when (type) {
            is TypeDefinition.Object -> completeObjectProperties(type, lastPart)
            is TypeDefinition.Array -> completeArrayAccess(type, lastPart)
            is TypeDefinition.Union -> completeUnion(type, lastPart)
            else -> emptyList()
        }
    }

    /**
     * Resolve a path to its type
     *
     * Example: "input.Order.Items" -> TypeDefinition.Array
     */
    private fun resolvePath(path: String): TypeDefinition? {
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
     * Complete object properties
     */
    private fun completeObjectProperties(
        type: TypeDefinition.Object,
        partial: String
    ): List<CompletionItem> {
        return type.properties
            .filter { (name, _) -> name.startsWith(partial, ignoreCase = true) }
            .map { (name, propertyType) ->
                val propType = propertyType.type
                CompletionItem(
                    label = name,
                    kind = when (propType) {
                        is TypeDefinition.Object -> CompletionItemKind.CLASS
                        is TypeDefinition.Array -> CompletionItemKind.ARRAY
                        else -> CompletionItemKind.PROPERTY
                    },
                    detail = formatTypeDetail(propType),
                    documentation = buildDocumentation(name, propType),
                    insertText = name,
                    sortText = rankCompletion(name, propType),
                    filterText = name
                )
            }
            .sortedBy { it.sortText }
    }

    /**
     * Complete array access patterns
     */
    private fun completeArrayAccess(
        type: TypeDefinition.Array,
        partial: String
    ): List<CompletionItem> {
        val suggestions = mutableListOf<CompletionItem>()

        // Suggest array indexing operators
        if ("[]".startsWith(partial, ignoreCase = true)) {
            suggestions.add(
                CompletionItem(
                    label = "[]",
                    kind = CompletionItemKind.OPERATOR,
                    detail = "Array element access",
                    documentation = "Access array elements by index",
                    insertText = "[]"
                )
            )
        }

        if ("[0]".startsWith(partial, ignoreCase = true)) {
            suggestions.add(
                CompletionItem(
                    label = "[0]",
                    kind = CompletionItemKind.OPERATOR,
                    detail = "First element",
                    documentation = "Access the first element of the array",
                    insertText = "[0]"
                )
            )
        }

        // If array of objects, suggest properties from element type
        when (val elementType = type.elementType) {
            is TypeDefinition.Object -> {
                suggestions.addAll(completeObjectProperties(elementType, partial))
            }
            else -> {
                // Array of primitives - no further completion
            }
        }

        return suggestions
    }

    /**
     * Complete union type members
     */
    private fun completeUnion(
        type: TypeDefinition.Union,
        partial: String
    ): List<CompletionItem> {
        // Merge completions from all union members
        return type.types.flatMap { memberType ->
            when (memberType) {
                is TypeDefinition.Object -> completeObjectProperties(memberType, partial)
                is TypeDefinition.Array -> completeArrayAccess(memberType, partial)
                else -> emptyList()
            }
        }.distinctBy { it.label }
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
     * Build documentation string for a property
     */
    private fun buildDocumentation(name: String, propType: TypeDefinition): String {
        return buildString {
            append("**Property**: `$name`\n\n")
            append("**Type**: `${formatTypeDetail(propType)}`\n\n")

            when (propType) {
                is TypeDefinition.Array -> {
                    append("Array of ${formatTypeDetail(propType.elementType)} elements\n\n")
                }
                is TypeDefinition.Object -> {
                    if (propType.properties.isNotEmpty()) {
                        append("Object with ${propType.properties.size} properties\n\n")
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Rank completion items for sorting
     *
     * Strategy:
     * - Required/common fields first
     * - Alphabetical within groups
     */
    private fun rankCompletion(name: String, propType: TypeDefinition): String {
        // Rank attributes (@ prefix) lower
        val prefix = if (name.startsWith("@")) "1" else "0"
        return "$prefix-$name"
    }
}

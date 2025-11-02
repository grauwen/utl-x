// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/completion/CompletionService.kt
package org.apache.utlx.daemon.completion

import org.apache.utlx.analysis.types.TypeContext
import org.apache.utlx.daemon.state.StateManager
import org.apache.utlx.daemon.state.DocumentMode
import org.slf4j.LoggerFactory

/**
 * Completion Service
 *
 * Main entry point for LSP completion requests.
 * Coordinates between document state, type environment, and path completion.
 */
class CompletionService(
    private val stateManager: StateManager
) {
    private val logger = LoggerFactory.getLogger(CompletionService::class.java)

    /**
     * Handle completion request
     *
     * @param params Completion parameters from LSP client
     * @return Completion list with suggestions
     */
    fun getCompletions(params: CompletionParams): CompletionList {
        val uri = params.textDocument.uri
        val position = params.position

        // Get document mode
        val mode = stateManager.getDocumentMode(uri)
        logger.debug("Completion request for $uri at ${position.line}:${position.character} (mode: ${mode.name})")

        // Get document text
        val text = stateManager.getDocumentText(uri)
        if (text == null) {
            logger.warn("Document not found: $uri")
            return CompletionList(isIncomplete = false, items = emptyList())
        }

        // Get type environment for this document
        val typeEnv = stateManager.getTypeEnvironment(uri)
        if (typeEnv == null) {
            logger.warn("No type environment for document: $uri")
            return CompletionList(isIncomplete = false, items = emptyList())
        }

        // Extract the partial path at cursor position
        val partialPath = extractPathAtPosition(text, position)
        logger.debug("Extracted partial path: '$partialPath'")

        if (partialPath == null) {
            // No path context at cursor
            return CompletionList(isIncomplete = false, items = emptyList())
        }

        // Get completions for the path
        val completer = PathCompleter(typeEnv)
        val items = completer.complete(partialPath)

        logger.debug("Found ${items.size} completion items")

        return CompletionList(
            isIncomplete = false,
            items = items
        )
    }

    /**
     * Extract the partial path at the given position
     *
     * Examples:
     * - "input.Order." → "input.Order."
     * - "input.Order.It" → "input.Order.It"
     * - "sum(input.Order.Items.Item.@price)" → "input.Order.Items.Item.@price"
     *
     * @param text Document text
     * @param position Cursor position
     * @return Partial path or null if not in a path context
     */
    fun extractPathAtPosition(text: String, position: Position): String? {
        val lines = text.lines()
        if (position.line >= lines.size) {
            return null
        }

        val line = lines[position.line]
        if (position.character > line.length) {
            return null
        }

        // Get text up to cursor
        val textBeforeCursor = line.substring(0, position.character)

        // Find the start of the current path expression
        // Look backwards for path start (alphanumeric or "input", "$", "@")
        var start = position.character - 1
        while (start >= 0) {
            val ch = textBeforeCursor[start]
            if (ch.isLetterOrDigit() || ch == '.' || ch == '@' || ch == '_') {
                start--
            } else if (ch == '$') {
                // Variable reference
                start--
                break
            } else {
                // Non-path character
                start++
                break
            }
        }

        if (start < 0) start = 0

        val partialPath = textBeforeCursor.substring(start).trim()

        // Validate that it looks like a path
        if (partialPath.isEmpty() || !isValidPathStart(partialPath)) {
            return null
        }

        return partialPath
    }

    /**
     * Check if a string looks like the start of a valid path
     */
    private fun isValidPathStart(text: String): Boolean {
        // Must start with "input", "$", or a letter
        return text.startsWith("input") ||
                text.startsWith("$") ||
                text.firstOrNull()?.isLetter() == true ||
                text.startsWith("@")
    }
}

// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/hover/HoverTypes.kt
package org.apache.utlx.daemon.hover

import org.apache.utlx.daemon.completion.Position
import org.apache.utlx.daemon.completion.TextDocumentIdentifier

/**
 * LSP Hover Request Parameters
 */
data class HoverParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position
)

/**
 * LSP Hover Response
 */
data class Hover(
    val contents: MarkupContent,
    val range: Range? = null
)

/**
 * LSP Markup Content
 */
data class MarkupContent(
    val kind: MarkupKind,
    val value: String
)

/**
 * Markup kind (plain text or markdown)
 */
enum class MarkupKind(val value: String) {
    PLAINTEXT("plaintext"),
    MARKDOWN("markdown")
}

/**
 * LSP Range
 */
data class Range(
    val start: Position,
    val end: Position
)

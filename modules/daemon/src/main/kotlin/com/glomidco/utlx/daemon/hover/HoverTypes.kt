// modules/daemon/src/main/kotlin/com/glomidco/utlx/daemon/hover/HoverTypes.kt
package com.glomidco.utlx.daemon.hover

import com.glomidco.utlx.daemon.completion.Position
import com.glomidco.utlx.daemon.completion.TextDocumentIdentifier

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

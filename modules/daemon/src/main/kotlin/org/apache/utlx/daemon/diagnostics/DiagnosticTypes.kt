// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/diagnostics/DiagnosticTypes.kt
package org.apache.utlx.daemon.diagnostics

import org.apache.utlx.daemon.hover.Range

/**
 * LSP Diagnostic
 *
 * Represents an error, warning, or other message in a document.
 */
data class Diagnostic(
    val range: Range,
    val severity: DiagnosticSeverity,
    val code: String? = null,
    val source: String = "utlx",
    val message: String,
    val relatedInformation: List<DiagnosticRelatedInformation>? = null
)

/**
 * LSP Diagnostic Severity
 */
enum class DiagnosticSeverity(val value: Int) {
    ERROR(1),
    WARNING(2),
    INFORMATION(3),
    HINT(4)
}

/**
 * LSP Diagnostic Related Information
 *
 * Additional information about a diagnostic (e.g., related locations)
 */
data class DiagnosticRelatedInformation(
    val location: Location,
    val message: String
)

/**
 * LSP Location
 */
data class Location(
    val uri: String,
    val range: Range
)

/**
 * Publish Diagnostics Parameters
 *
 * Parameters for the textDocument/publishDiagnostics notification
 */
data class PublishDiagnosticsParams(
    val uri: String,
    val version: Int? = null,
    val diagnostics: List<Diagnostic>
)

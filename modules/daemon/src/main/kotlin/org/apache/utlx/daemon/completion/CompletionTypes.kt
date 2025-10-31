// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/completion/CompletionTypes.kt
package org.apache.utlx.daemon.completion

/**
 * LSP Completion Item
 *
 * Represents a single completion suggestion returned to the IDE.
 */
data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind,
    val detail: String,
    val documentation: String? = null,
    val insertText: String = label,
    val sortText: String = label,
    val filterText: String = label
)

/**
 * LSP Completion Item Kind
 *
 * Categorizes completion items for IDE rendering.
 */
enum class CompletionItemKind(val value: Int) {
    TEXT(1),
    METHOD(2),
    FUNCTION(3),
    CONSTRUCTOR(4),
    FIELD(5),
    VARIABLE(6),
    CLASS(7),
    INTERFACE(8),
    MODULE(9),
    PROPERTY(10),
    UNIT(11),
    VALUE(12),
    ENUM(13),
    KEYWORD(14),
    SNIPPET(15),
    COLOR(16),
    FILE(17),
    REFERENCE(18),
    FOLDER(19),
    ENUM_MEMBER(20),
    CONSTANT(21),
    STRUCT(22),
    EVENT(23),
    OPERATOR(24),
    TYPE_PARAMETER(25);

    companion object {
        // Convenience aliases for UTL-X usage
        val ARRAY = CLASS
        val OBJECT = CLASS
    }
}

/**
 * Position in a text document
 */
data class Position(
    val line: Int,      // 0-based
    val character: Int  // 0-based
)

/**
 * Text document identifier
 */
data class TextDocumentIdentifier(
    val uri: String
)

/**
 * Completion context
 */
data class CompletionContext(
    val triggerKind: CompletionTriggerKind,
    val triggerCharacter: String? = null
)

/**
 * How the completion was triggered
 */
enum class CompletionTriggerKind(val value: Int) {
    INVOKED(1),                              // User invoked (Ctrl+Space)
    TRIGGER_CHARACTER(2),                    // Trigger character typed (.)
    TRIGGER_FOR_INCOMPLETE_COMPLETIONS(3)    // Re-triggered for incomplete
}

/**
 * Completion request parameters
 */
data class CompletionParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    val context: CompletionContext? = null
)

/**
 * Completion result (list of items)
 */
data class CompletionList(
    val isIncomplete: Boolean,
    val items: List<CompletionItem>
)

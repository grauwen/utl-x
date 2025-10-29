# UTL-X: Unified Validation & LSP Architecture

**Document Version:** 1.0
**Last Updated:** 2025-10-29
**Status:** Architecture Design
**Author:** UTL-X Architecture Team
**Related Documents:**
- [validation-and-analysis-study.md](./validation-and-analysis-study.md) - Foundation document

---

## Executive Summary

This document presents a **unified layered architecture** for validation, linting, and Language Server Protocol (LSP) integration in UTL-X. The key insight is that all three components (CLI validate, CLI lint, and LSP/IDE integration) share the same validation infrastructure but differ in how they present results and when they trigger.

**Key Architectural Principles:**

1. **Single Source of Truth**: One validation engine serves all consumers (CLI, LSP, build tools)
2. **Layered Design**: Clear separation between validation logic and presentation
3. **Incremental Analysis**: Support both full-file and incremental validation for LSP performance
4. **Monaco Editor Integration**: LSP designed specifically for VS Code's Monaco editor
5. **Shared Error Model**: Common diagnostic model across all consumers

**Visual Overview:**

```
┌───────────────────────────────────────────────────────────────┐
│                     CONSUMER LAYER                            │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │   CLI        │  │   CLI        │  │   LSP Server       │  │
│  │  validate    │  │   lint       │  │  (Monaco/VS Code)  │  │
│  └──────┬───────┘  └──────┬───────┘  └─────────┬──────────┘  │
│         │                 │                     │             │
└─────────┼─────────────────┼─────────────────────┼─────────────┘
          │                 │                     │
          └─────────────────┴─────────────────────┘
                            │
┌───────────────────────────┼─────────────────────────────────────┐
│                   FACADE LAYER                                  │
│         ┌─────────────────▼──────────────────┐                  │
│         │   ValidationFacade                 │                  │
│         │   - Orchestrates validation        │                  │
│         │   - Caches results                 │                  │
│         │   - Manages incremental updates    │                  │
│         └─────────────────┬──────────────────┘                  │
└───────────────────────────┼─────────────────────────────────────┘
                            │
┌───────────────────────────┼─────────────────────────────────────┐
│                    VALIDATION ENGINE                            │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌──────────────┐   │
│  │ Syntax   │  │ Semantic │  │  Schema   │  │   Logical    │   │
│  │Validator │  │Validator │  │ Validator │  │  (Linter)    │   │
│  └────┬─────┘  └────┬─────┘  └─────┬─────┘  └──────┬───────┘   │
│       │             │               │               │           │
│       └─────────────┴───────────────┴───────────────┘           │
│                            │                                    │
│                  ┌─────────▼──────────┐                         │
│                  │  Diagnostic Model  │                         │
│                  │  - Location        │                         │
│                  │  - Severity        │                         │
│                  │  - Message         │                         │
│                  │  - Quick fixes     │                         │
│                  └────────────────────┘                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Table of Contents

1. [LSP and Monaco Editor Background](#1-lsp-and-monaco-editor-background)
2. [Shared Validation Infrastructure](#2-shared-validation-infrastructure)
3. [Layered Architecture Design](#3-layered-architecture-design)
4. [LSP Implementation](#4-lsp-implementation)
5. [Incremental Validation Strategy](#5-incremental-validation-strategy)
6. [Monaco Editor Integration](#6-monaco-editor-integration)
7. [CLI Integration](#7-cli-integration)
8. [Performance Considerations](#8-performance-considerations)
9. [Implementation Roadmap](#9-implementation-roadmap)
10. [Example Workflows](#10-example-workflows)

---

## 1. LSP and Monaco Editor Background

### 1.1 What is LSP?

**Language Server Protocol (LSP)** is a protocol between an editor/IDE and a language server that provides language-specific features:

- **Diagnostics** (errors, warnings)
- **Auto-completion** (IntelliSense)
- **Hover information** (documentation)
- **Go to definition**
- **Find references**
- **Code actions** (quick fixes)
- **Formatting**

**Key Properties:**
- Editor-agnostic (VS Code, IntelliJ, Vim, Emacs all support LSP)
- JSON-RPC based communication
- Incremental updates for performance
- Rich error reporting with ranges and severity

### 1.2 Monaco Editor vs VS Code

**Monaco Editor** is the code editor that powers VS Code, but there's an important distinction:

```
┌─────────────────────────────────────────────┐
│           Visual Studio Code                │
│  ┌───────────────────────────────────────┐  │
│  │        Monaco Editor (Core)           │  │
│  │  - Syntax highlighting                │  │
│  │  - Text editing                       │  │
│  │  - Basic operations                   │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  + Extensions                               │
│  + LSP Client                               │
│  + File system                              │
│  + Debugger                                 │
│  + Terminal                                 │
└─────────────────────────────────────────────┘
```

**Monaco Editor (standalone):**
- ✅ Can be embedded in web applications
- ✅ Has built-in LSP support (via `monaco-languageclient`)
- ✅ Lightweight, browser-based
- ❌ Fewer features than full VS Code

**For UTL-X:**
- **Primary target**: VS Code extension (full LSP implementation)
- **Secondary target**: Monaco standalone (for web-based UTL-X playground)
- Both use the same LSP server implementation

### 1.3 LSP Lifecycle

```
┌────────────────┐                    ┌────────────────┐
│   VS Code /    │    JSON-RPC        │   UTL-X        │
│ Monaco Editor  │  ◄──────────────►  │ Language Server│
└────────────────┘                    └────────────────┘

Lifecycle:
1. initialize        → Server capabilities negotiation
2. initialized       → Server ready
3. textDocument/didOpen → Document opened in editor
4. textDocument/didChange → Document edited (incremental)
5. textDocument/publishDiagnostics → Server sends errors/warnings
6. textDocument/completion → User requests autocomplete
7. textDocument/hover → User hovers over symbol
8. textDocument/definition → User requests "Go to definition"
9. textDocument/didClose → Document closed
10. shutdown → Server shutting down
```

### 1.4 Why LSP Matters for UTL-X

**Without LSP:**
- Users must save file and run `utlx validate` manually
- No real-time feedback
- No inline documentation
- No autocomplete

**With LSP:**
- ✅ **Instant feedback**: Errors appear as you type
- ✅ **Inline suggestions**: "Did you mean 'customer'?"
- ✅ **Autocomplete**: Suggest stdlib functions, variables
- ✅ **Documentation**: Hover to see function signatures
- ✅ **Navigation**: Jump to function definitions
- ✅ **Quick fixes**: Apply suggested corrections with one click

**Example - Real-time error in Monaco/VS Code:**

```utlx
%utlx 1.0
input xml
output json
---
{
  customer: $input.Order.customr.Name
                          ~~~~~~
                          ⚠️ Undefined field 'customr'
                          💡 Did you mean 'customer'?
}
```

---

## 2. Shared Validation Infrastructure

### 2.1 Core Principle: One Engine, Multiple Consumers

**Problem**: Without shared infrastructure, we'd duplicate logic:

```
❌ BAD APPROACH (Duplication):
CLI Validate → ValidatorV1
CLI Lint     → LinterV1
LSP Server   → ValidatorV2 + LinterV2
```

**Solution**: Single validation engine with multiple facades:

```
✅ GOOD APPROACH (Shared):
                     ┌─── CLI Validate
ValidationEngine ───┼─── CLI Lint
                     └─── LSP Server
```

### 2.2 Unified Diagnostic Model

**Core abstraction**: A diagnostic represents any issue found during analysis.

```kotlin
/**
 * Universal diagnostic model
 * Used by CLI, LSP, build tools, etc.
 */
data class Diagnostic(
    /** Severity level */
    val severity: Severity,

    /** Source location */
    val location: Location,

    /** Primary message */
    val message: String,

    /** Error code (for documentation lookup) */
    val code: String? = null,

    /** Detailed explanation */
    val explanation: String? = null,

    /** Related locations (e.g., where variable was defined) */
    val relatedLocations: List<RelatedLocation> = emptyList(),

    /** Quick fixes available */
    val fixes: List<QuickFix> = emptyList(),

    /** Tags for categorization */
    val tags: Set<DiagnosticTag> = emptySet()
)

enum class Severity {
    ERROR,      // Prevents compilation
    WARNING,    // Potential bug
    INFO,       // Informational message
    HINT        // Suggestion
}

data class Location(
    val file: String,
    val line: Int,
    val column: Int,
    val endLine: Int = line,
    val endColumn: Int = column + 1,
    val label: String? = null
)

data class RelatedLocation(
    val location: Location,
    val message: String
)

data class QuickFix(
    val title: String,
    val edits: List<TextEdit>
)

data class TextEdit(
    val range: Range,
    val newText: String
)

data class Range(
    val start: Position,
    val end: Position
)

data class Position(
    val line: Int,     // 0-indexed
    val character: Int // 0-indexed
)

enum class DiagnosticTag {
    UNNECESSARY,  // Unused code (can be grayed out)
    DEPRECATED    // Deprecated feature
}
```

**Why this model?**
- ✅ Rich enough for LSP (ranges, related locations, fixes)
- ✅ Simple enough for CLI (can format as text)
- ✅ Extensible (tags, custom data)
- ✅ Matches LSP diagnostic structure closely

### 2.3 Validation Levels Recap

From the validation study document, we have **4 validation levels**:

```
Level 1: Syntactic Validation
  ├─ Lexical analysis
  ├─ Parsing
  └─ AST construction

Level 2: Semantic Validation
  ├─ Type checking
  ├─ Variable resolution
  ├─ Function validation
  └─ Scope analysis

Level 3: Schema Validation
  ├─ Design-time path checking
  └─ Runtime data validation

Level 4: Logical Validation (Linting)
  ├─ Dead code detection
  ├─ Unused variables
  ├─ Complexity analysis
  └─ Style checking
```

**Key insight**: Each level produces diagnostics that flow to consumers.

---

## 3. Layered Architecture Design

### 3.1 Architecture Layers

```
┌────────────────────────────────────────────────────────────────────┐
│  LAYER 1: CONSUMER LAYER (Presentation)                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────────┐   │
│  │   CLI    │  │   CLI    │  │   LSP    │  │   Build Tool    │   │
│  │ validate │  │   lint   │  │  Server  │  │    Plugin       │   │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────────┬────────┘   │
│       │             │              │                  │            │
└───────┼─────────────┼──────────────┼──────────────────┼────────────┘
        │             │              │                  │
        └─────────────┴──────────────┴──────────────────┘
                            │
┌───────────────────────────┼────────────────────────────────────────┐
│  LAYER 2: FACADE LAYER (Orchestration)                            │
│                  ┌────────▼─────────┐                              │
│                  │ ValidationFacade │                              │
│                  │ ================ │                              │
│                  │ + validate()     │                              │
│                  │ + lint()         │                              │
│                  │ + validateIncr() │                              │
│                  │ + caching        │                              │
│                  └────────┬─────────┘                              │
└───────────────────────────┼────────────────────────────────────────┘
                            │
┌───────────────────────────┼────────────────────────────────────────┐
│  LAYER 3: VALIDATION ENGINE (Core Logic)                          │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│   │   Syntactic  │  │   Semantic   │  │   Logical    │           │
│   │  Validator   │  │  Validator   │  │  (Linter)    │           │
│   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
│          │                  │                 │                   │
│          └──────────────────┴─────────────────┘                   │
│                            │                                      │
│                 ┌──────────▼───────────┐                          │
│                 │ DiagnosticCollector  │                          │
│                 └──────────────────────┘                          │
└───────────────────────────────────────────────────────────────────┘
                            │
┌───────────────────────────┼────────────────────────────────────────┐
│  LAYER 4: FOUNDATION (Shared Infrastructure)                      │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐    │
│   │  Parser  │  │   AST    │  │  Types   │  │  Diagnostic  │    │
│   │          │  │          │  │          │  │    Model     │    │
│   └──────────┘  └──────────┘  └──────────┘  └──────────────┘    │
└───────────────────────────────────────────────────────────────────┘
```

### 3.2 Layer Responsibilities

#### Layer 1: Consumer Layer (Presentation)

**Responsibility**: Present diagnostics to end users in consumer-specific format.

**Components:**
- **CLI Validate**: Format diagnostics as terminal output with colors
- **CLI Lint**: Format warnings with suggestions
- **LSP Server**: Convert diagnostics to LSP protocol messages
- **Gradle/Maven Plugin**: Convert to build tool format

**Does NOT**:
- ❌ Contain validation logic
- ❌ Parse UTL-X code
- ❌ Know about validation levels

#### Layer 2: Facade Layer (Orchestration)

**Responsibility**: Coordinate validation execution, manage caching, handle incremental updates.

**Components:**
- **ValidationFacade**: Main entry point for all validation
  - Decides which validators to run
  - Manages caching of AST and diagnostics
  - Handles incremental validation (for LSP)
  - Aggregates diagnostics from all validators

**Key Methods:**
```kotlin
class ValidationFacade {
    /** Full validation (CLI, first LSP request) */
    fun validate(
        source: String,
        config: ValidationConfig
    ): ValidationResult

    /** Incremental validation (LSP document changes) */
    fun validateIncremental(
        documentId: String,
        changes: List<TextDocumentContentChangeEvent>,
        config: ValidationConfig
    ): ValidationResult

    /** Lint only (assumes valid syntax) */
    fun lint(
        source: String,
        config: LintConfig
    ): List<Diagnostic>

    /** Clear cache for document */
    fun invalidate(documentId: String)
}
```

#### Layer 3: Validation Engine (Core Logic)

**Responsibility**: Execute validation levels, produce diagnostics.

**Components:**
- **SyntacticValidator**: Lexer + Parser errors
- **SemanticValidator**: Type checking, variable resolution, function validation
- **SchemaValidator**: Path validation against XSD/JSON Schema
- **LogicalValidator (Linter)**: Dead code, unused vars, style
- **DiagnosticCollector**: Aggregates diagnostics from all validators

**Key Properties:**
- ✅ No I/O operations (pure logic)
- ✅ Testable in isolation
- ✅ No knowledge of consumers (CLI, LSP, etc.)
- ✅ Fast (optimized for LSP performance)

#### Layer 4: Foundation (Shared Infrastructure)

**Responsibility**: Provide core data structures and parsing.

**Components:**
- **Parser**: `parser_impl.kt` - already exists
- **AST**: `ast_nodes.kt` - already exists
- **Type System**: `type_system.kt` - already exists
- **Diagnostic Model**: Shared error representation

---

## 4. LSP Implementation

### 4.1 LSP Server Architecture

```kotlin
/**
 * UTL-X Language Server
 * Implements org.eclipse.lsp4j.services.LanguageServer
 */
class UTLXLanguageServer : LanguageServer {

    private val facade = ValidationFacade()
    private val documents = DocumentManager()

    /**
     * Called when server starts
     */
    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities().apply {
            // Text document synchronization (incremental)
            textDocumentSync = TextDocumentSyncKind.Incremental

            // Diagnostics (errors, warnings)
            diagnosticProvider = DiagnosticOptions()

            // Code completion
            completionProvider = CompletionOptions(
                resolveProvider = true,
                triggerCharacters = listOf(".", "$", "@")
            )

            // Hover for documentation
            hoverProvider = true

            // Go to definition
            definitionProvider = true

            // Find references
            referencesProvider = true

            // Code actions (quick fixes)
            codeActionProvider = true

            // Document formatting
            documentFormattingProvider = true
        }

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    /**
     * Called when document opens
     */
    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        val content = params.textDocument.text

        documents.open(uri, content)

        // Run full validation
        validateAndPublish(uri, content)
    }

    /**
     * Called when document changes (as user types)
     */
    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = params.textDocument.uri
        val changes = params.contentChanges

        // Update document
        documents.applyChanges(uri, changes)

        // Run incremental validation
        validateIncrementalAndPublish(uri, changes)
    }

    /**
     * Called when document saved
     */
    override fun didSave(params: DidSaveTextDocumentParams) {
        val uri = params.textDocument.uri
        val content = documents.getContent(uri)

        // Re-run full validation on save
        validateAndPublish(uri, content)
    }

    /**
     * Called when document closes
     */
    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = params.textDocument.uri

        documents.close(uri)
        facade.invalidate(uri)

        // Clear diagnostics
        client.publishDiagnostics(PublishDiagnosticsParams(uri, emptyList()))
    }

    /**
     * Run validation and publish diagnostics
     */
    private fun validateAndPublish(uri: String, content: String) {
        GlobalScope.launch {
            val result = facade.validate(
                source = content,
                config = ValidationConfig(
                    enableSyntax = true,
                    enableSemantic = true,
                    enableSchema = false, // Schema optional
                    enableLint = true     // Include linting
                )
            )

            val diagnostics = result.diagnostics.map { toDiagnostic(it) }

            client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }
    }

    /**
     * Run incremental validation and publish
     */
    private fun validateIncrementalAndPublish(
        uri: String,
        changes: List<TextDocumentContentChangeEvent>
    ) {
        GlobalScope.launch {
            val result = facade.validateIncremental(
                documentId = uri,
                changes = changes,
                config = ValidationConfig(
                    enableSyntax = true,
                    enableSemantic = true,
                    enableSchema = false,
                    enableLint = false  // Skip linting for incremental (too slow)
                )
            )

            val diagnostics = result.diagnostics.map { toDiagnostic(it) }

            client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }
    }

    /**
     * Convert internal diagnostic to LSP diagnostic
     */
    private fun toDiagnostic(diag: Diagnostic): LSPDiagnostic {
        return LSPDiagnostic(
            range = Range(
                Position(diag.location.line - 1, diag.location.column - 1),
                Position(diag.location.endLine - 1, diag.location.endColumn - 1)
            ),
            severity = when (diag.severity) {
                Severity.ERROR -> DiagnosticSeverity.Error
                Severity.WARNING -> DiagnosticSeverity.Warning
                Severity.INFO -> DiagnosticSeverity.Information
                Severity.HINT -> DiagnosticSeverity.Hint
            },
            source = "utlx",
            message = diag.message,
            code = diag.code,
            relatedInformation = diag.relatedLocations.map {
                DiagnosticRelatedInformation(
                    location = toLocation(it.location),
                    message = it.message
                )
            },
            tags = diag.tags.mapNotNull { tag ->
                when (tag) {
                    DiagnosticTag.UNNECESSARY -> LSPDiagnosticTag.Unnecessary
                    DiagnosticTag.DEPRECATED -> LSPDiagnosticTag.Deprecated
                }
            }
        )
    }
}
```

### 4.2 LSP Features Implementation

#### 4.2.1 Code Completion

```kotlin
/**
 * Provide completion suggestions
 */
override fun completion(params: CompletionParams): CompletableFuture<CompletionList> {
    val uri = params.textDocument.uri
    val position = params.position
    val content = documents.getContent(uri)

    return CompletableFuture.supplyAsync {
        val suggestions = completionProvider.getCompletions(
            source = content,
            line = position.line + 1,
            column = position.character + 1
        )

        val items = suggestions.map { suggestion ->
            CompletionItem(suggestion.label).apply {
                kind = suggestion.kind
                detail = suggestion.detail
                documentation = suggestion.documentation
                insertText = suggestion.insertText
                insertTextFormat = InsertTextFormat.Snippet
            }
        }

        CompletionList(false, items)
    }
}

/**
 * Completion provider
 */
class CompletionProvider(private val stdlib: StandardLibrary) {

    fun getCompletions(
        source: String,
        line: Int,
        column: Int
    ): List<CompletionSuggestion> {
        val suggestions = mutableListOf<CompletionSuggestion>()

        // Parse to get context
        val parseResult = parser.parse(source)
        if (parseResult !is ParseResult.Success) {
            return suggestions
        }

        val ast = parseResult.program
        val context = findContext(ast, line, column)

        // Suggest stdlib functions
        if (context is FunctionCallContext) {
            suggestions.addAll(
                stdlib.functions.map { func ->
                    CompletionSuggestion(
                        label = func.name,
                        kind = CompletionItemKind.Function,
                        detail = func.signature,
                        documentation = func.description,
                        insertText = buildSnippet(func)
                    )
                }
            )
        }

        // Suggest variables in scope
        if (context is VariableContext) {
            suggestions.addAll(
                findVariablesInScope(ast, line, column).map { varName ->
                    CompletionSuggestion(
                        label = varName,
                        kind = CompletionItemKind.Variable,
                        detail = "Local variable",
                        insertText = varName
                    )
                }
            )
        }

        // Suggest $input fields (from schema if available)
        if (context is FieldAccessContext && context.base == "input") {
            // TODO: Use schema to suggest fields
        }

        return suggestions
    }

    private fun buildSnippet(func: StdlibFunction): String {
        val params = func.parameters.mapIndexed { i, param ->
            "${i + 1}:\${${i + 1}:${param.name}}"
        }.joinToString(", ")

        return "${func.name}($params)"
    }
}
```

#### 4.2.2 Hover Documentation

```kotlin
/**
 * Provide hover information
 */
override fun hover(params: HoverParams): CompletableFuture<Hover> {
    val uri = params.textDocument.uri
    val position = params.position
    val content = documents.getContent(uri)

    return CompletableFuture.supplyAsync {
        val hoverInfo = hoverProvider.getHover(
            source = content,
            line = position.line + 1,
            column = position.character + 1
        )

        if (hoverInfo != null) {
            Hover(
                MarkupContent(
                    kind = MarkupKind.MARKDOWN,
                    value = hoverInfo.markdown
                ),
                hoverInfo.range
            )
        } else {
            null
        }
    }
}

/**
 * Hover provider
 */
class HoverProvider(private val stdlib: StandardLibrary) {

    fun getHover(source: String, line: Int, column: Int): HoverInfo? {
        val parseResult = parser.parse(source)
        if (parseResult !is ParseResult.Success) {
            return null
        }

        val ast = parseResult.program
        val symbol = findSymbolAt(ast, line, column) ?: return null

        return when (symbol) {
            is FunctionSymbol -> {
                val func = stdlib.getFunction(symbol.name)
                HoverInfo(
                    markdown = buildMarkdown(func),
                    range = symbol.range
                )
            }

            is VariableSymbol -> {
                val type = inferType(symbol, ast)
                HoverInfo(
                    markdown = """
                        **Variable**: `${symbol.name}`
                        **Type**: `$type`
                    """.trimIndent(),
                    range = symbol.range
                )
            }

            else -> null
        }
    }

    private fun buildMarkdown(func: StdlibFunction): String {
        return """
            ### ${func.name}

            ```utlx
            ${func.signature}
            ```

            ${func.description}

            **Parameters:**
            ${func.parameters.joinToString("\n") { "- `${it.name}`: ${it.type} - ${it.description}" }}

            **Returns:** ${func.returnType}

            **Example:**
            ```utlx
            ${func.example}
            ```
        """.trimIndent()
    }
}
```

#### 4.2.3 Code Actions (Quick Fixes)

```kotlin
/**
 * Provide code actions (quick fixes)
 */
override fun codeAction(params: CodeActionParams): CompletableFuture<List<CodeAction>> {
    val uri = params.textDocument.uri
    val range = params.range
    val diagnostics = params.context.diagnostics

    return CompletableFuture.supplyAsync {
        val actions = mutableListOf<CodeAction>()

        diagnostics.forEach { diag ->
            // Find internal diagnostic with fixes
            val internalDiag = findInternalDiagnostic(uri, diag)

            internalDiag?.fixes?.forEach { fix ->
                actions.add(
                    CodeAction(fix.title).apply {
                        kind = CodeActionKind.QuickFix
                        diagnostics = listOf(diag)
                        edit = WorkspaceEdit(
                            mapOf(uri to fix.edits.map { textEdit ->
                                LSPTextEdit(textEdit.range, textEdit.newText)
                            })
                        )
                    }
                )
            }
        }

        actions
    }
}
```

### 4.3 Document Management

```kotlin
/**
 * Manages open documents in editor
 */
class DocumentManager {
    private val documents = ConcurrentHashMap<String, Document>()

    fun open(uri: String, content: String) {
        documents[uri] = Document(uri, content, version = 1)
    }

    fun applyChanges(uri: String, changes: List<TextDocumentContentChangeEvent>) {
        val doc = documents[uri] ?: return

        var content = doc.content

        // Apply changes in order
        changes.forEach { change ->
            content = if (change.range == null) {
                // Full document update
                change.text
            } else {
                // Incremental update
                applyChange(content, change.range, change.text)
            }
        }

        documents[uri] = doc.copy(
            content = content,
            version = doc.version + 1
        )
    }

    fun getContent(uri: String): String {
        return documents[uri]?.content ?: ""
    }

    fun close(uri: String) {
        documents.remove(uri)
    }

    private fun applyChange(content: String, range: Range, newText: String): String {
        val lines = content.lines().toMutableList()

        // Calculate character offsets
        val startOffset = lines.take(range.start.line).sumOf { it.length + 1 } + range.start.character
        val endOffset = lines.take(range.end.line).sumOf { it.length + 1 } + range.end.character

        val before = content.substring(0, startOffset)
        val after = content.substring(endOffset)

        return before + newText + after
    }
}

data class Document(
    val uri: String,
    val content: String,
    val version: Int
)
```

---

## 5. Incremental Validation Strategy

### 5.1 Why Incremental Validation?

**Problem**: Full re-parsing on every keystroke is too slow for LSP.

**Example scenario:**
- User types in a 1000-line UTL-X file
- Every keystroke triggers `didChange`
- Full parse + validate takes 100ms
- User experience: **laggy, unresponsive**

**Solution**: Incremental validation
- Only re-validate changed sections
- Reuse cached AST nodes for unchanged code
- Target: <20ms latency per keystroke

### 5.2 Incremental Strategy

```kotlin
/**
 * Incremental validation facade
 */
class ValidationFacade {
    private val cache = ConcurrentHashMap<String, CachedValidation>()

    fun validateIncremental(
        documentId: String,
        changes: List<TextDocumentContentChangeEvent>,
        config: ValidationConfig
    ): ValidationResult {
        val cached = cache[documentId]

        if (cached == null) {
            // No cache, do full validation
            return validate(documents.getContent(documentId), config)
        }

        // Analyze changes
        val changeAnalysis = analyzeChanges(cached, changes)

        return when (changeAnalysis) {
            is FullRevalidation -> {
                // Too many changes, re-validate everything
                validate(documents.getContent(documentId), config)
            }

            is PartialRevalidation -> {
                // Re-validate affected regions only
                validatePartial(cached, changeAnalysis.affectedRegions, config)
            }
        }
    }

    private fun analyzeChanges(
        cached: CachedValidation,
        changes: List<TextDocumentContentChangeEvent>
    ): ChangeAnalysis {
        // If too many changes, do full revalidation
        if (changes.size > 10) {
            return FullRevalidation
        }

        val affectedRegions = mutableListOf<Region>()

        changes.forEach { change ->
            val range = change.range ?: return FullRevalidation

            // Find AST nodes affected by this range
            val affectedNodes = cached.ast.findNodesInRange(
                startLine = range.start.line + 1,
                endLine = range.end.line + 1
            )

            affectedRegions.addAll(affectedNodes.map { it.region })
        }

        return PartialRevalidation(affectedRegions)
    }

    private fun validatePartial(
        cached: CachedValidation,
        affectedRegions: List<Region>,
        config: ValidationConfig
    ): ValidationResult {
        val diagnostics = mutableListOf<Diagnostic>()

        // Keep diagnostics from unaffected regions
        diagnostics.addAll(
            cached.diagnostics.filter { diag ->
                affectedRegions.none { region ->
                    region.contains(diag.location)
                }
            }
        )

        // Re-validate affected regions
        affectedRegions.forEach { region ->
            val regionSource = extractSource(documents.getContent(cached.documentId), region)
            val regionDiags = validateRegion(regionSource, region, config)
            diagnostics.addAll(regionDiags)
        }

        return ValidationResult(diagnostics)
    }
}

data class CachedValidation(
    val documentId: String,
    val version: Int,
    val ast: Program,
    val diagnostics: List<Diagnostic>,
    val timestamp: Long
)

sealed class ChangeAnalysis
object FullRevalidation : ChangeAnalysis()
data class PartialRevalidation(val affectedRegions: List<Region>) : ChangeAnalysis()

data class Region(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int
) {
    fun contains(location: Location): Boolean {
        return location.line >= startLine && location.line <= endLine
    }
}
```

### 5.3 Performance Optimization: Smart Caching

```kotlin
/**
 * AST caching with incremental updates
 */
class ASTCache {
    private val cache = ConcurrentHashMap<String, CachedAST>()

    fun get(documentId: String, version: Int): Program? {
        val cached = cache[documentId]
        return if (cached?.version == version) cached.ast else null
    }

    fun put(documentId: String, version: Int, ast: Program) {
        cache[documentId] = CachedAST(version, ast, System.currentTimeMillis())

        // Evict old entries (LRU-like)
        evictOldEntries()
    }

    fun invalidate(documentId: String) {
        cache.remove(documentId)
    }

    private fun evictOldEntries() {
        val now = System.currentTimeMillis()
        val maxAge = 5 * 60 * 1000 // 5 minutes

        cache.entries.removeIf { (_, cached) ->
            now - cached.timestamp > maxAge
        }
    }
}

data class CachedAST(
    val version: Int,
    val ast: Program,
    val timestamp: Long
)
```

### 5.4 Throttling and Debouncing

```kotlin
/**
 * Debounce rapid changes (user typing fast)
 */
class DebouncedValidator(
    private val facade: ValidationFacade,
    private val delay: Long = 300 // ms
) {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val pendingTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()

    fun validateDebounced(
        documentId: String,
        content: String,
        config: ValidationConfig,
        callback: (ValidationResult) -> Unit
    ) {
        // Cancel pending validation for this document
        pendingTasks[documentId]?.cancel(false)

        // Schedule new validation
        val task = scheduler.schedule({
            val result = facade.validate(content, config)
            callback(result)
            pendingTasks.remove(documentId)
        }, delay, TimeUnit.MILLISECONDS)

        pendingTasks[documentId] = task
    }
}
```

**Usage in LSP server:**

```kotlin
class UTLXLanguageServer : LanguageServer {
    private val debouncedValidator = DebouncedValidator(facade, delay = 300)

    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = params.textDocument.uri
        val content = documents.getContent(uri)

        // Debounce validation (wait for user to stop typing)
        debouncedValidator.validateDebounced(
            documentId = uri,
            content = content,
            config = ValidationConfig(/*...*/)
        ) { result ->
            val diagnostics = result.diagnostics.map { toDiagnostic(it) }
            client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }
    }
}
```

---

## 6. Monaco Editor Integration

### 6.1 Monaco Standalone vs VS Code Extension

**Two deployment scenarios:**

1. **VS Code Extension** (Primary)
   - Full LSP client
   - Installed via VS Code marketplace
   - Access to file system, terminal, etc.

2. **Monaco Standalone** (Web Playground)
   - Browser-based editor
   - WebSocket connection to LSP server
   - No file system access

### 6.2 VS Code Extension Structure

```
utlx-vscode/
├── package.json              # Extension manifest
├── syntaxes/
│   └── utlx.tmLanguage.json  # Syntax highlighting
├── language-configuration.json # Brackets, comments
├── src/
│   ├── extension.ts          # Extension entry point
│   ├── language-client.ts    # LSP client setup
│   └── commands/             # Custom commands
└── server/
    └── utlx-language-server.jar # LSP server JAR
```

**package.json:**

```json
{
  "name": "utlx",
  "displayName": "UTL-X",
  "description": "Language support for UTL-X transformation language",
  "version": "0.1.0",
  "engines": {
    "vscode": "^1.75.0"
  },
  "categories": ["Programming Languages"],
  "activationEvents": ["onLanguage:utlx"],
  "main": "./out/extension.js",
  "contributes": {
    "languages": [{
      "id": "utlx",
      "aliases": ["UTL-X", "utlx"],
      "extensions": [".utlx"],
      "configuration": "./language-configuration.json"
    }],
    "grammars": [{
      "language": "utlx",
      "scopeName": "source.utlx",
      "path": "./syntaxes/utlx.tmLanguage.json"
    }],
    "commands": [
      {
        "command": "utlx.validate",
        "title": "UTL-X: Validate Transformation"
      },
      {
        "command": "utlx.lint",
        "title": "UTL-X: Lint Transformation"
      }
    ],
    "configuration": {
      "title": "UTL-X",
      "properties": {
        "utlx.validation.enabled": {
          "type": "boolean",
          "default": true,
          "description": "Enable real-time validation"
        },
        "utlx.linting.enabled": {
          "type": "boolean",
          "default": true,
          "description": "Enable linting"
        }
      }
    }
  },
  "dependencies": {
    "vscode-languageclient": "^8.0.2"
  }
}
```

**extension.ts:**

```typescript
import * as vscode from 'vscode';
import * as path from 'path';
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext) {
  // Path to language server JAR
  const serverPath = context.asAbsolutePath(
    path.join('server', 'utlx-language-server.jar')
  );

  // Server options: Start language server as Java process
  const serverOptions: ServerOptions = {
    run: {
      command: 'java',
      args: ['-jar', serverPath],
      transport: TransportKind.stdio
    },
    debug: {
      command: 'java',
      args: ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005', '-jar', serverPath],
      transport: TransportKind.stdio
    }
  };

  // Client options: Which files to monitor
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'utlx' }],
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.utlx')
    }
  };

  // Create language client
  client = new LanguageClient(
    'utlxLanguageServer',
    'UTL-X Language Server',
    serverOptions,
    clientOptions
  );

  // Start client (and server)
  client.start();

  // Register commands
  context.subscriptions.push(
    vscode.commands.registerCommand('utlx.validate', async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor || editor.document.languageId !== 'utlx') {
        vscode.window.showErrorMessage('No UTL-X file active');
        return;
      }

      await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: 'Validating UTL-X transformation...'
      }, async () => {
        // Trigger validation via language server
        await vscode.commands.executeCommand('vscode.executeCodeActionProvider',
          editor.document.uri,
          new vscode.Range(0, 0, 0, 0)
        );
      });
    })
  );
}

export function deactivate(): Thenable<void> | undefined {
  if (!client) {
    return undefined;
  }
  return client.stop();
}
```

### 6.3 Monaco Standalone (Web Playground)

**HTML:**

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>UTL-X Playground</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/monaco-editor@0.40.0/min/vs/editor/editor.main.css">
</head>
<body>
  <div id="container" style="width:800px;height:600px;border:1px solid grey"></div>

  <script src="https://cdn.jsdelivr.net/npm/monaco-editor@0.40.0/min/vs/loader.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/monaco-languageclient@6.0.0/lib/index.js"></script>
  <script src="utlx-playground.js"></script>
</body>
</html>
```

**utlx-playground.js:**

```javascript
require.config({ paths: { 'vs': 'https://cdn.jsdelivr.net/npm/monaco-editor@0.40.0/min/vs' }});

require(['vs/editor/editor.main'], function() {
  // Register UTL-X language
  monaco.languages.register({ id: 'utlx' });

  // Define syntax highlighting
  monaco.languages.setMonarchTokensProvider('utlx', {
    keywords: ['input', 'output', 'let', 'function', 'if', 'else', 'match', 'case'],
    operators: ['|>', '=>', '+', '-', '*', '/'],
    tokenizer: {
      root: [
        [/[a-z_$][\w$]*/, {
          cases: {
            '@keywords': 'keyword',
            '@default': 'identifier'
          }
        }],
        [/\$[a-z_][\w$]*/, 'variable'],
        [/"([^"\\]|\\.)*$/, 'string.invalid'],
        [/"/, 'string', '@string'],
        [/\d+/, 'number']
      ],
      string: [
        [/[^\\"]+/, 'string'],
        [/"/, 'string', '@pop']
      ]
    }
  });

  // Create editor
  const editor = monaco.editor.create(document.getElementById('container'), {
    value: '%utlx 1.0\ninput xml\noutput json\n---\n{\n  result: $input.data\n}',
    language: 'utlx',
    theme: 'vs-dark'
  });

  // Connect to language server via WebSocket
  const webSocket = new WebSocket('ws://localhost:3000/utlx-lsp');

  const languageClient = new MonacoLanguageClient.MonacoLanguageClient({
    name: 'UTL-X Language Client',
    clientOptions: {
      documentSelector: ['utlx'],
      synchronize: {},
    },
    connectionProvider: {
      get: (errorHandler, closeHandler) => {
        return Promise.resolve({
          reader: new WebSocketMessageReader(webSocket),
          writer: new WebSocketMessageWriter(webSocket)
        });
      }
    }
  });

  languageClient.start();
});
```

**LSP Server WebSocket Adapter:**

```kotlin
/**
 * WebSocket adapter for browser-based Monaco editor
 */
class WebSocketLSPServer(port: Int) {
    private val server = NettyWebSocketServer(port)
    private val lspServer = UTLXLanguageServer()

    fun start() {
        server.start { socket ->
            // Wrap WebSocket as JSON-RPC channel
            val inputStream = WebSocketInputStream(socket)
            val outputStream = WebSocketOutputStream(socket)

            val launcher = LSPLauncher.createServerLauncher(
                lspServer,
                inputStream,
                outputStream
            )

            launcher.startListening()
        }
    }
}
```

---

## 7. CLI Integration

### 7.1 CLI Commands Using Shared Facade

**Both CLI commands use the same ValidationFacade:**

```kotlin
/**
 * CLI Validate command
 */
@Command(name = "validate", description = "Validate UTL-X transformation")
class ValidateCommand : Callable<Int> {

    @Parameters(index = "0", description = "UTL-X file to validate")
    lateinit var file: String

    @Option(names = ["--schema"], description = "Input schema file")
    var schemaFile: String? = null

    private val facade = ValidationFacade()
    private val formatter = TerminalErrorFormatter()

    override fun call(): Int {
        val source = File(file).readText()

        // Use shared facade
        val result = facade.validate(
            source = source,
            config = ValidationConfig(
                enableSyntax = true,
                enableSemantic = true,
                enableSchema = schemaFile != null,
                schemaFile = schemaFile,
                enableLint = false  // validate doesn't lint
            )
        )

        if (result.diagnostics.isEmpty()) {
            println("✓ Validation passed")
            return 0
        }

        // Format for terminal
        result.diagnostics.forEach { diag ->
            println(formatter.format(diag, source))
        }

        val errorCount = result.diagnostics.count { it.severity == Severity.ERROR }
        println("\n✗ Validation failed: $errorCount error(s)")

        return 1
    }
}

/**
 * CLI Lint command
 */
@Command(name = "lint", description = "Lint UTL-X transformation")
class LintCommand : Callable<Int> {

    @Parameters(index = "0", description = "UTL-X file to lint")
    lateinit var file: String

    @Option(names = ["--fix"], description = "Auto-fix issues")
    var fix: Boolean = false

    private val facade = ValidationFacade()
    private val formatter = TerminalErrorFormatter()

    override fun call(): Int {
        val source = File(file).readText()

        // Use shared facade
        val warnings = facade.lint(
            source = source,
            config = LintConfig(
                enableDeadCode = true,
                enableUnusedVars = true,
                enableComplexity = true,
                enableStyle = true
            )
        )

        if (warnings.isEmpty()) {
            println("✓ No lint issues found")
            return 0
        }

        // Format for terminal
        warnings.forEach { diag ->
            println(formatter.format(diag, source))
        }

        val fixableCount = warnings.count { it.fixes.isNotEmpty() }
        println("\n⚠️  ${warnings.size} warning(s) ($fixableCount fixable)")

        if (fix && fixableCount > 0) {
            val fixed = applyFixes(source, warnings)
            File(file).writeText(fixed)
            println("✓ Fixed $fixableCount issue(s)")
        }

        return 0  // Lint never fails
    }
}
```

### 7.2 Terminal Error Formatting

```kotlin
/**
 * Format diagnostics for terminal output
 */
class TerminalErrorFormatter {

    fun format(diag: Diagnostic, source: String): String {
        return buildString {
            // Header with color
            append(formatHeader(diag))
            append("\n")

            // Location
            append("  --> ${diag.location.file}:${diag.location.line}:${diag.location.column}")
            append("\n")

            // Code snippet
            append(formatCodeSnippet(diag.location, source))
            append("\n")

            // Explanation
            if (diag.explanation != null) {
                append("  = explanation: ${diag.explanation}")
                append("\n")
            }

            // Fixes
            if (diag.fixes.isNotEmpty()) {
                append("  💡 Quick fix available: ${diag.fixes[0].title}")
                append("\n")
            }
        }
    }

    private fun formatHeader(diag: Diagnostic): String {
        val color = when (diag.severity) {
            Severity.ERROR -> ANSI.RED
            Severity.WARNING -> ANSI.YELLOW
            Severity.INFO -> ANSI.CYAN
            Severity.HINT -> ANSI.GREEN
        }

        val label = diag.severity.name.lowercase()
        val code = if (diag.code != null) "[${diag.code}]" else ""

        return "$color$label$code${ANSI.RESET}: ${diag.message}"
    }

    private fun formatCodeSnippet(location: Location, source: String): String {
        val lines = source.lines()
        val lineIndex = location.line - 1

        if (lineIndex !in lines.indices) {
            return ""
        }

        return buildString {
            // Line number
            append(String.format("%4d", location.line))
            append(" | ")
            append(lines[lineIndex])
            append("\n")

            // Pointer
            append("     | ")
            append(" ".repeat(location.column - 1))
            append("${ANSI.RED}^${ANSI.RESET}")

            if (location.endColumn > location.column + 1) {
                append("${ANSI.RED}${"^".repeat(location.endColumn - location.column - 1)}${ANSI.RESET}")
            }
        }
    }
}

object ANSI {
    const val RESET = "\u001B[0m"
    const val RED = "\u001B[31m"
    const val YELLOW = "\u001B[33m"
    const val CYAN = "\u001B[36m"
    const val GREEN = "\u001B[32m"
}
```

---

## 8. Performance Considerations

### 8.1 Performance Targets

| Operation | Target | Critical For |
|-----------|--------|--------------|
| Full parse + validate | <100ms | CLI, LSP initial |
| Incremental validate | <20ms | LSP typing |
| Lint (full file) | <200ms | CLI lint |
| Code completion | <50ms | LSP completion |
| Hover information | <30ms | LSP hover |

### 8.2 Optimization Strategies

#### 8.2.1 Parser Optimization

```kotlin
/**
 * Optimized parser with early exit
 */
class OptimizedParser(tokens: List<Token>) : Parser(tokens) {

    /**
     * Parse with early error limit
     */
    fun parseWithLimit(maxErrors: Int = 10): ParseResult {
        try {
            val program = parseProgram()

            // Stop parsing if too many errors
            if (errors.size >= maxErrors) {
                return ParseResult.Failure(errors + ParseError(
                    message = "Too many errors, stopping parse",
                    location = Location.unknown
                ))
            }

            return if (errors.isEmpty()) {
                ParseResult.Success(program)
            } else {
                ParseResult.Failure(errors)
            }
        } catch (e: ParseException) {
            return ParseResult.Failure(errors + ParseError(e.message, e.location))
        }
    }
}
```

#### 8.2.2 Parallel Validation

```kotlin
/**
 * Run validators in parallel for performance
 */
class ParallelValidationEngine {

    fun validate(
        ast: Program,
        config: ValidationConfig
    ): List<Diagnostic> {
        val diagnostics = ConcurrentLinkedQueue<Diagnostic>()
        val jobs = mutableListOf<CompletableFuture<Unit>>()

        // Syntax already done (have AST)

        // Semantic validation (parallel)
        if (config.enableSemantic) {
            jobs.add(CompletableFuture.runAsync {
                val semanticErrors = semanticValidator.validate(ast)
                diagnostics.addAll(semanticErrors)
            })
        }

        // Schema validation (parallel)
        if (config.enableSchema && config.schemaFile != null) {
            jobs.add(CompletableFuture.runAsync {
                val schemaErrors = schemaValidator.validate(ast, config.schemaFile)
                diagnostics.addAll(schemaErrors)
            })
        }

        // Linting (parallel)
        if (config.enableLint) {
            jobs.add(CompletableFuture.runAsync {
                val lintWarnings = linter.analyze(ast)
                diagnostics.addAll(lintWarnings)
            })
        }

        // Wait for all
        CompletableFuture.allOf(*jobs.toTypedArray()).join()

        return diagnostics.toList()
    }
}
```

#### 8.2.3 Caching Strategy

```kotlin
/**
 * Multi-level caching
 */
class CachingValidationFacade : ValidationFacade {

    // Level 1: AST cache (parse results)
    private val astCache = ASTCache()

    // Level 2: Type cache (semantic analysis results)
    private val typeCache = TypeCache()

    // Level 3: Diagnostic cache (full validation results)
    private val diagnosticCache = DiagnosticCache()

    override fun validate(
        source: String,
        config: ValidationConfig
    ): ValidationResult {
        val hash = source.hashCode()

        // Check diagnostic cache first
        val cached = diagnosticCache.get(hash, config)
        if (cached != null) {
            return cached
        }

        // Check AST cache
        var ast = astCache.get(hash)
        if (ast == null) {
            val parseResult = parser.parse(source)
            if (parseResult is ParseResult.Failure) {
                return ValidationResult(parseResult.errors.map { toDiagnostic(it) })
            }
            ast = (parseResult as ParseResult.Success).program
            astCache.put(hash, ast)
        }

        // Run validation
        val diagnostics = engine.validate(ast, config)
        val result = ValidationResult(diagnostics)

        // Cache result
        diagnosticCache.put(hash, config, result)

        return result
    }
}
```

### 8.3 Profiling and Monitoring

```kotlin
/**
 * Performance monitoring for validation
 */
class ProfiledValidationFacade(private val delegate: ValidationFacade) : ValidationFacade {

    private val metrics = ConcurrentHashMap<String, PerformanceMetrics>()

    override fun validate(source: String, config: ValidationConfig): ValidationResult {
        val start = System.nanoTime()

        val result = delegate.validate(source, config)

        val duration = (System.nanoTime() - start) / 1_000_000.0 // ms

        recordMetric("validate", duration)

        if (duration > 100) {
            logger.warn("Slow validation: ${duration}ms for ${source.length} characters")
        }

        return result
    }

    private fun recordMetric(operation: String, duration: Double) {
        val metric = metrics.computeIfAbsent(operation) { PerformanceMetrics() }
        metric.record(duration)
    }

    fun getMetrics(): Map<String, PerformanceMetrics> {
        return metrics.toMap()
    }
}

class PerformanceMetrics {
    private val durations = ConcurrentLinkedQueue<Double>()

    fun record(duration: Double) {
        durations.add(duration)

        // Keep only last 100 samples
        while (durations.size > 100) {
            durations.poll()
        }
    }

    fun average(): Double {
        return durations.average()
    }

    fun p95(): Double {
        val sorted = durations.sorted()
        val index = (sorted.size * 0.95).toInt()
        return sorted.getOrElse(index) { 0.0 }
    }
}
```

---

## 9. Implementation Roadmap

### Phase 1: Foundation (Weeks 1-2)

**Goals:**
- Implement shared diagnostic model
- Create ValidationFacade
- Refactor existing validation to use facade

**Tasks:**
1. Week 1: Diagnostic model
   - Implement `Diagnostic` data class
   - Implement `Location`, `QuickFix`, etc.
   - Write converters (CLI, LSP)

2. Week 2: ValidationFacade
   - Implement facade pattern
   - Integrate existing validators
   - Add basic caching

**Deliverables:**
- ✅ `Diagnostic` model
- ✅ `ValidationFacade` implementation
- ✅ CLI commands using facade

### Phase 2: LSP Server (Weeks 3-5)

**Goals:**
- Implement LSP server
- Add basic features (diagnostics, hover)
- Test with VS Code

**Tasks:**
1. Week 3: LSP infrastructure
   - Implement `UTLXLanguageServer`
   - Add document management
   - Implement lifecycle methods

2. Week 4: Core LSP features
   - Diagnostics publishing
   - Hover provider
   - Code completion (basic)

3. Week 5: Testing and refinement
   - Test with VS Code
   - Fix bugs
   - Performance tuning

**Deliverables:**
- ✅ LSP server JAR
- ✅ Basic VS Code extension
- ✅ Real-time diagnostics working

### Phase 3: VS Code Extension (Weeks 6-7)

**Goals:**
- Create VS Code extension
- Add syntax highlighting
- Publish to marketplace

**Tasks:**
1. Week 6: Extension development
   - Package.json configuration
   - Syntax highlighting (TextMate grammar)
   - Language client setup

2. Week 7: Polish and publish
   - Icon and branding
   - Documentation
   - Marketplace submission

**Deliverables:**
- ✅ VS Code extension published
- ✅ Installation guide

### Phase 4: Incremental Validation (Weeks 8-9)

**Goals:**
- Implement incremental validation
- Add caching strategies
- Optimize for real-time performance

**Tasks:**
1. Week 8: Incremental algorithm
   - Change analysis
   - Partial revalidation
   - AST node reuse

2. Week 9: Optimization
   - Debouncing
   - Parallel validation
   - Performance profiling

**Deliverables:**
- ✅ <20ms incremental validation
- ✅ Performance benchmarks

### Phase 5: Advanced LSP Features (Weeks 10-11)

**Goals:**
- Code actions (quick fixes)
- Go to definition
- Find references

**Tasks:**
1. Week 10: Quick fixes
   - Implement `QuickFix` generation
   - Code action provider
   - Auto-fix integration

2. Week 11: Navigation features
   - Symbol table construction
   - Go to definition
   - Find references

**Deliverables:**
- ✅ Quick fixes working
- ✅ Navigation features

### Phase 6: Monaco Playground (Weeks 12-13)

**Goals:**
- Web-based UTL-X playground
- Browser-compatible LSP
- Online documentation integration

**Tasks:**
1. Week 12: Monaco integration
   - WebSocket LSP adapter
   - Browser build
   - UI design

2. Week 13: Playground features
   - Example transformations
   - Share functionality
   - Documentation integration

**Deliverables:**
- ✅ https://playground.utl-x.dev
- ✅ Interactive examples

### Phase 7: Production Ready (Weeks 14-15)

**Goals:**
- Performance optimization
- Comprehensive testing
- Documentation

**Tasks:**
1. Week 14: Performance
   - Profile and optimize
   - Load testing
   - Memory optimization

2. Week 15: Documentation
   - LSP API docs
   - Extension guide
   - Tutorial videos

**Deliverables:**
- ✅ Production-ready LSP
- ✅ Complete documentation

---

## 10. Example Workflows

### 10.1 Developer Workflow (VS Code)

**Scenario:** User writing a transformation in VS Code

```
1. Open transform.utlx in VS Code
   └─→ LSP: didOpen
       └─→ ValidationFacade.validate()
           └─→ Publish diagnostics

2. User types: let customr = $input.Order.Customer
                    ~~~~~~
   └─→ LSP: didChange (incremental)
       └─→ DebouncedValidator (wait 300ms)
           └─→ ValidationFacade.validateIncremental()
               └─→ Publish diagnostics
                   └─→ VS Code shows: ⚠️ Undefined variable 'customr'
                                      💡 Did you mean 'customer'?

3. User hovers over 'map'
   └─→ LSP: hover
       └─→ HoverProvider.getHover()
           └─→ Show stdlib function documentation

4. User types: map(items,
   └─→ LSP: completion
       └─→ CompletionProvider.getCompletions()
           └─→ Suggest: item => { ... }

5. User clicks quick fix "Change to 'customer'"
   └─→ LSP: codeAction
       └─→ Apply text edit
           └─→ Code updated automatically
```

### 10.2 CI/CD Workflow

**Scenario:** GitHub Actions build pipeline

```yaml
name: Validate UTL-X

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Install UTL-X
        run: curl -fsSL https://utl-x.dev/install.sh | bash

      - name: Validate all transformations
        run: |
          EXIT_CODE=0
          for file in $(find . -name "*.utlx"); do
            echo "Validating $file..."
            if ! utlx validate "$file"; then
              EXIT_CODE=1
            fi
          done
          exit $EXIT_CODE

      - name: Lint (warnings only)
        run: |
          for file in $(find . -name "*.utlx"); do
            utlx lint "$file"
          done
        continue-on-error: true
```

**Process:**

```
1. Push to GitHub
   └─→ Trigger workflow

2. For each .utlx file:
   └─→ utlx validate
       └─→ ValidationFacade.validate()
           └─→ If errors: EXIT_CODE=1

3. For each .utlx file:
   └─→ utlx lint
       └─→ ValidationFacade.lint()
           └─→ Print warnings (don't fail)

4. Build fails if validation errors found
```

### 10.3 Pre-commit Hook Workflow

**Scenario:** Developer commits code

```bash
# .git/hooks/pre-commit

#!/bin/bash

STAGED_UTLX=$(git diff --cached --name-only --diff-filter=ACM | grep ".utlx$")

if [ -z "$STAGED_UTLX" ]; then
  exit 0
fi

echo "Running UTL-X validation..."

FAILED=0
for FILE in $STAGED_UTLX; do
  utlx validate "$FILE"
  if [ $? -ne 0 ]; then
    FAILED=1
  fi
done

if [ $FAILED -eq 0 ]; then
  echo "Auto-fixing lint issues..."
  for FILE in $STAGED_UTLX; do
    utlx lint "$FILE" --fix
    git add "$FILE"
  done
fi

exit $FAILED
```

**Process:**

```
1. git commit
   └─→ Pre-commit hook triggered

2. Find staged .utlx files
   └─→ Validate each file
       └─→ ValidationFacade.validate()
           └─→ If errors: FAILED=1

3. If no errors:
   └─→ Lint and auto-fix
       └─→ ValidationFacade.lint()
           └─→ Apply fixes
           └─→ Re-stage files

4. Exit with FAILED status
   └─→ If FAILED=1: Commit blocked
   └─→ If FAILED=0: Commit proceeds
```

### 10.4 Playground Workflow (Monaco)

**Scenario:** User trying UTL-X online

```
1. Visit https://playground.utl-x.dev
   └─→ Load Monaco editor
       └─→ Connect to LSP via WebSocket

2. User selects "Example: XML to JSON"
   └─→ Load example code into editor
       └─→ LSP: didOpen
           └─→ ValidationFacade.validate()
               └─→ Publish diagnostics

3. User modifies code
   └─→ LSP: didChange
       └─→ Real-time validation
           └─→ Show errors inline

4. User clicks "Run Transformation"
   └─→ Send to execution API
       └─→ Return result
           └─→ Display in output panel

5. User clicks "Share"
   └─→ Generate shareable URL
       └─→ Save code to database
       └─→ Return short link
```

---

## Conclusion

### Key Architectural Decisions

1. **Unified Validation Engine**: Single source of truth serves CLI, LSP, and build tools
2. **Layered Design**: Clear separation between validation logic (engine) and presentation (consumers)
3. **Shared Diagnostic Model**: Common error representation across all consumers
4. **Incremental Validation**: Optimized for real-time LSP performance (<20ms)
5. **Monaco/VS Code Integration**: LSP designed specifically for these editors

### Benefits of This Architecture

✅ **No Code Duplication**: Validation logic written once, used everywhere
✅ **Consistent Experience**: Same errors in CLI, IDE, and CI/CD
✅ **Performance**: Incremental updates, caching, parallelization
✅ **Maintainability**: Clear layer boundaries, testable components
✅ **Extensibility**: Easy to add new consumers (IntelliJ plugin, etc.)

### Implementation Priority

**High Priority (Core):**
1. ✅ Validation facade with diagnostic model
2. ✅ LSP server with basic features
3. ✅ VS Code extension
4. ✅ Incremental validation

**Medium Priority (Enhanced):**
5. ✅ Advanced LSP features (quick fixes, navigation)
6. ✅ Monaco playground
7. ✅ Performance optimization

**Low Priority (Nice-to-Have):**
8. ⏳ IntelliJ IDEA plugin
9. ⏳ Sublime Text LSP integration
10. ⏳ Vim/Neovim LSP support

### Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| LSP latency (typing) | <20ms | 95th percentile |
| Initial validation | <100ms | Average |
| Code completion time | <50ms | 95th percentile |
| Memory usage (LSP) | <100MB | Steady state |
| VS Code extension rating | >4.0/5.0 | Marketplace |
| Adoption rate | 50+ installs | First month |

---

**Document Status:** Ready for Implementation
**Author:** UTL-X Architecture Team
**Last Updated:** 2025-10-29
**Version:** 1.0

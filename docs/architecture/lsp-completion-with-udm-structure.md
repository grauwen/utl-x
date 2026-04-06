# LSP Completion with UDM Structure

## Date: 2025-11-12

## Problem Statement

The UTLX editor widget needs intelligent dropdown completions based on actual input data structure:

1. **When user types `$`** → Show list of available inputs (e.g., `$employees`, `$orders`, `$customers`)
2. **When user types `$input.`** → Show fields/properties from that specific input's UDM structure (e.g., `Department`, `Salary`, `EmployeeID` for CSV with those headers)

## Current Architecture

### Data Flow Overview

```
Input Files (CSV/JSON/XML)
        ↓
Multi-Input Widget (stores raw content)
        ↓
[NO CONNECTION TO LSP CURRENTLY]
        ↓
Editor Widget (Monaco)
        ↓ (LSP Protocol)
LSP Daemon (DaemonServer)
        ↓
CompletionService → PathCompleter
        ↓
[Uses Type Environment from schemas ONLY]
```

### Key Components

#### 1. UDM (Universal Data Model)
**Location**: `/modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt`

The UDM represents all data formats uniformly:

```kotlin
sealed class UDM {
    data class Scalar(val value: Any?)
    data class Array(val elements: List<UDM>)
    data class Object(
        val properties: Map<String, UDM>,    // ← Field names stored here!
        val attributes: Map<String, String>,
        val name: String?,
        val metadata: Map<String, String>
    )
    // ... other types
}
```

**Field names extracted from**:
- **CSV with headers**: First row becomes property keys in each UDM.Object
- **JSON objects**: Property keys directly
- **XML elements**: Element/attribute names as property keys

#### 2. InputMetadataExtractor
**Location**: `/modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/InputMetadataExtractor.kt`

Already exists! Can extract field names from UDM:

```kotlin
fun extract(name: String, udm: UDM, format: String): InputMetadata {
    val fields = extractFieldNames(udm)  // Returns List<String> of field names
    // ...
}

private fun extractFieldNames(udm: UDM): List<String>? {
    return when (udm) {
        is UDM.Array -> {
            val firstElement = udm.elements.firstOrNull()
            if (firstElement is UDM.Object) {
                firstElement.properties.keys.toList()  // ← CSV headers!
            } else null
        }
        is UDM.Object -> udm.properties.keys.toList()
        else -> null
    }
}
```

#### 3. LSP Daemon
**Location**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt`

Current LSP methods:
- `initialize` - Client initialization
- `textDocument/didOpen` - Document opened
- `textDocument/didChange` - Document changed
- `textDocument/completion` - Completion request ← **We need to enhance this**
- `textDocument/hover` - Hover information
- `textDocument/diagnostic` - Diagnostics

#### 4. StateManager
**Location**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/state/StateManager.kt`

Currently stores:
```kotlin
private val documents: ConcurrentHashMap<String, DocumentState>
private val typeEnvironments: ConcurrentHashMap<String, TypeContext>  // Schema-based
private val schemas: ConcurrentHashMap<String, SchemaInfo>
private val documentModes: ConcurrentHashMap<String, DocumentMode>
```

**Missing**: Cache for actual input data (UDM or InputMetadata)

#### 5. CompletionService
**Location**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/completion/CompletionService.kt`

Current flow:
1. Extract partial path at cursor position (e.g., `input.Order.Cust`)
2. Use PathCompleter with TypeContext (schema-based)
3. Return completion items

**Missing**: Logic to use actual input data for completions

#### 6. Multi-Input Panel Widget
**Location**: `/theia-extension/utlx-theia-extension/src/browser/input-panel/multi-input-panel-widget.tsx`

Currently:
```typescript
interface InputTab {
    id: string;
    name: string;
    instanceContent: string;      // ← Raw CSV/JSON/XML content
    instanceFormat: InstanceFormat;
    schemaContent: string;
    // ...
}
```

**Missing**: Connection to LSP to register input data

---

## Solution Approaches

### Approach A: Pre-Parse Input Data ✅

**How it works**:
1. When input loaded in multi-input widget, parse immediately
2. Convert to UDM using format parser
3. Extract field names using InputMetadataExtractor
4. Send to LSP daemon via new `utlx/registerInputData` method
5. StateManager caches the metadata
6. CompletionService uses cached data for suggestions

**Pros**:
- ✅ Immediate feedback when input loaded
- ✅ Accurate field names from actual data
- ✅ Works with CSV headers, JSON keys, XML elements
- ✅ Handles both single and multiple inputs
- ✅ InputMetadataExtractor already exists
- ✅ Works in RUNTIME mode (user's primary use case)

**Cons**:
- ❌ Parse overhead for large files (mitigated: only parse once on load)
- ❌ Memory overhead (mitigated: only store field list, not full UDM)
- ❌ Requires adding new LSP method

**Complexity**: Medium (3-6 files to modify)

---

### Approach B: Lazy Parsing ⚠️

**How it works**:
1. Store raw input content in StateManager
2. Parse only when completion triggered
3. Cache result for subsequent completions
4. Invalidate cache when input changes

**Pros**:
- ✅ Lower memory when completion not used
- ✅ Handles large files better (parse on-demand)

**Cons**:
- ❌ Slower first completion (user waits for parse)
- ❌ Complex cache invalidation logic
- ❌ Bad UX: delay on first `$input.` trigger
- ❌ Still requires parsing eventually

**Complexity**: High (complex caching logic)

---

### Approach C: Schema-Based Only ⚠️

**How it works**:
1. In DESIGN_TIME mode, use XSD/JSON Schema
2. Extract types from schema
3. Suggest based on schema structure

**Pros**:
- ✅ Already partially implemented
- ✅ Lightweight (no data parsing)
- ✅ Works for design-time validation

**Cons**:
- ❌ **ONLY works in DESIGN_TIME mode** (requires schema)
- ❌ Does NOT work in RUNTIME mode (no schema)
- ❌ Misses dynamic fields not in schema
- ❌ Doesn't meet user requirements

**Complexity**: Low (mostly exists), but incomplete solution

---

### Approach D: Hybrid (Schema + Data) ✅✅ RECOMMENDED

**How it works**:
1. **DESIGN_TIME mode**: Use schema if available (Approach C)
2. **RUNTIME mode**: Use actual input data (Approach A)
3. **Fallback**: If no schema and no data, basic suggestions

**Pros**:
- ✅ Works in BOTH modes
- ✅ Optimal for each scenario
- ✅ Leverages existing infrastructure
- ✅ Best UX across all use cases
- ✅ Future-proof architecture

**Cons**:
- ❌ More code paths to maintain
- ❌ Need to implement both schema and data paths

**Complexity**: Medium-High (combines A + C)

---

## Recommended Solution: Hybrid Approach (D)

Implement in two phases:

### Phase 1: Runtime Mode with Actual Data (Priority)
Focus on user's primary use case: RUNTIME mode with actual CSV/JSON/XML files

### Phase 2: Schema-Based for Design-Time (Enhancement)
Add schema-based completion for design-time validation workflows

---

## Implementation Plan

### Phase 1: Runtime Mode with Input Data

#### Step 1: Add LSP Method `utlx/registerInputData`

**File**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt`

Add new method handler:

```kotlin
private fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
    return when (request.method) {
        // ... existing methods
        "utlx/registerInputData" -> handleRegisterInputData(request)
        // ...
    }
}

private fun handleRegisterInputData(request: JsonRpcRequest): JsonRpcResponse {
    val params = request.params as? Map<*, *>
        ?: return JsonRpcResponse.invalidParams(request.id, "Invalid params")

    val documentUri = params["uri"] as? String
        ?: return JsonRpcResponse.invalidParams(request.id, "Missing uri")
    val inputName = params["inputName"] as? String
        ?: return JsonRpcResponse.invalidParams(request.id, "Missing inputName")
    val format = params["format"] as? String
        ?: return JsonRpcResponse.invalidParams(request.id, "Missing format")
    val content = params["content"] as? String
        ?: return JsonRpcResponse.invalidParams(request.id, "Missing content")

    try {
        // Parse content to UDM using format parser
        val udm = parseContentToUDM(content, format)

        // Extract metadata
        val metadata = InputMetadataExtractor.extract(inputName, udm, format)

        // Store in StateManager
        stateManager.registerInputData(documentUri, inputName, metadata)

        logger.info { "Registered input data: $inputName for $documentUri (${metadata.fields?.size ?: 0} fields)" }

        return JsonRpcResponse.success(request.id, mapOf(
            "success" to true,
            "fieldCount" to (metadata.fields?.size ?: 0)
        ))
    } catch (e: Exception) {
        logger.error(e) { "Failed to register input data: ${e.message}" }
        return JsonRpcResponse.error(request.id, -32603, "Failed to parse input: ${e.message}")
    }
}

private fun parseContentToUDM(content: String, format: String): UDM {
    return when (format.lowercase()) {
        "csv" -> {
            val parser = CSVParser(content, CSVDialect.DEFAULT)
            parser.parse(hasHeaders = true)
        }
        "json" -> {
            val parser = JSONParser(content)
            parser.parse()
        }
        "xml" -> {
            val parser = XMLParser(content)
            parser.parse()
        }
        "yaml" -> {
            val parser = YAMLParser(content)
            parser.parse()
        }
        else -> throw IllegalArgumentException("Unsupported format: $format")
    }
}
```

#### Step 2: Extend StateManager

**File**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/state/StateManager.kt`

Add input data cache:

```kotlin
class StateManager {
    private val documents = ConcurrentHashMap<String, DocumentState>()
    private val typeEnvironments = ConcurrentHashMap<String, TypeContext>()
    private val schemas = ConcurrentHashMap<String, SchemaInfo>()
    private val documentModes = ConcurrentHashMap<String, DocumentMode>()

    // NEW: Cache for input data metadata
    private val inputDataCache = ConcurrentHashMap<String, MutableMap<String, InputMetadata>>()

    /**
     * Register input data for a document
     *
     * @param uri Document URI
     * @param inputName Name of input (e.g., "employees", "orders")
     * @param metadata Extracted metadata with field names
     */
    fun registerInputData(uri: String, inputName: String, metadata: InputMetadata) {
        val docInputs = inputDataCache.getOrPut(uri) { ConcurrentHashMap() }
        docInputs[inputName] = metadata

        statistics.incrementCounter("inputDataRegistered")
    }

    /**
     * Get field names for a specific input
     *
     * @param uri Document URI
     * @param inputName Name of input
     * @return List of field names or null if not found
     */
    fun getInputFields(uri: String, inputName: String): List<String>? {
        return inputDataCache[uri]?.get(inputName)?.fields
    }

    /**
     * Get all registered inputs for a document
     *
     * @param uri Document URI
     * @return Map of input name to metadata, or null if none registered
     */
    fun getAllInputs(uri: String): Map<String, InputMetadata>? {
        return inputDataCache[uri]
    }

    /**
     * Get all input names for a document (for `$` completion)
     *
     * @param uri Document URI
     * @return List of input names
     */
    fun getInputNames(uri: String): List<String> {
        return inputDataCache[uri]?.keys?.toList() ?: emptyList()
    }

    /**
     * Clear input data for a document (when document closed)
     */
    fun clearInputData(uri: String) {
        inputDataCache.remove(uri)
    }

    // Update existing clearState() to also clear input data
    fun clearState() {
        documents.clear()
        typeEnvironments.clear()
        schemas.clear()
        documentModes.clear()
        inputDataCache.clear()  // NEW
        statistics.reset()
    }
}
```

#### Step 3: Enhance CompletionService

**File**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/completion/CompletionService.kt`

Modify to use input data:

```kotlin
fun getCompletions(params: CompletionParams): CompletionList {
    val uri = params.textDocument.uri
    val position = params.position

    val documentState = stateManager.getDocument(uri)
        ?: return CompletionList(false, emptyList())

    val text = documentState.text
    val offset = positionToOffset(text, position)

    // Extract partial path at cursor
    val partialPath = extractPathAtPosition(text, offset)

    // Check if this is an input field access pattern: $inputName.
    val inputAccessPattern = Regex("""\$(\w+)\.(.*)""")
    val inputMatch = inputAccessPattern.matchEntire(partialPath)

    if (inputMatch != null) {
        // User is accessing fields of a specific input
        val inputName = inputMatch.groupValues[1]
        val fieldPrefix = inputMatch.groupValues[2]

        // Try to get fields from actual input data
        val fields = stateManager.getInputFields(uri, inputName)

        if (fields != null) {
            // Use actual field names from input data
            return buildFieldCompletions(fields, fieldPrefix)
        }
    }

    // Check if this is a $ trigger (listing all inputs)
    if (partialPath == "$" || partialPath.matches(Regex("""\$\w*"""))) {
        val inputNames = stateManager.getInputNames(uri)
        if (inputNames.isNotEmpty()) {
            return buildInputNameCompletions(inputNames, partialPath)
        }
    }

    // Fallback to existing type-based completion (schema-based)
    val typeEnv = stateManager.getTypeEnvironment(uri)
    return if (typeEnv != null) {
        PathCompleter(typeEnv).complete(partialPath)
    } else {
        CompletionList(false, emptyList())
    }
}

private fun buildFieldCompletions(fields: List<String>, prefix: String): CompletionList {
    val items = fields
        .filter { it.startsWith(prefix, ignoreCase = true) }
        .map { fieldName ->
            CompletionItem(
                label = fieldName,
                kind = CompletionItemKind.Field,
                detail = "Field from input data",
                documentation = "Field extracted from actual input data (CSV header/JSON key/XML element)",
                insertText = fieldName
            )
        }

    return CompletionList(isIncomplete = false, items = items)
}

private fun buildInputNameCompletions(inputNames: List<String>, prefix: String): CompletionList {
    val searchPrefix = prefix.removePrefix("$")

    val items = inputNames
        .filter { it.startsWith(searchPrefix, ignoreCase = true) }
        .map { inputName ->
            CompletionItem(
                label = "$" + inputName,
                kind = CompletionItemKind.Variable,
                detail = "Input variable",
                documentation = "Input data source registered in multi-input panel",
                insertText = inputName  // Don't include $, it's already typed
            )
        }

    return CompletionList(isIncomplete = false, items = items)
}
```

#### Step 4: Register Inputs from Widget

**File**: `/theia-extension/utlx-theia-extension/src/browser/input-panel/multi-input-panel-widget.tsx`

Add LSP registration when input loaded:

```typescript
import { injectable, inject } from '@theia/core/shared/inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
// ... other imports

@injectable()
export class MultiInputPanelWidget extends ReactWidget {
    // ... existing code

    private async registerInputWithLSP(
        inputName: string,
        content: string,
        format: string
    ): Promise<void> {
        try {
            // Get current document URI from editor widget
            const documentUri = this.getCurrentDocumentUri();
            if (!documentUri) {
                console.warn('No active document, skipping LSP registration');
                return;
            }

            // Call LSP daemon
            const response = await fetch('http://localhost:7779/api/rpc', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    jsonrpc: '2.0',
                    id: Date.now(),
                    method: 'utlx/registerInputData',
                    params: {
                        uri: documentUri,
                        inputName: inputName,
                        format: format,
                        content: content
                    }
                })
            });

            const result = await response.json();

            if (result.error) {
                console.error('LSP registration failed:', result.error);
            } else {
                console.log(`Registered input '${inputName}' with LSP (${result.result.fieldCount} fields)`);
            }
        } catch (error) {
            console.error('Failed to register input with LSP:', error);
            // Don't fail the input loading if LSP registration fails
        }
    }

    private getCurrentDocumentUri(): string | null {
        // Get from editor widget or active editor
        // This might need to be injected or accessed via message service
        // For now, return a placeholder
        return 'file:///current-document.utlx';  // TODO: Get from actual editor
    }

    // Modify existing handleLoadFile to call LSP registration
    private async handleLoadFile(): Promise<void> {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.csv,.json,.xml,.yaml,.yml';

        input.onchange = async (e: Event) => {
            const file = (e.target as HTMLInputElement).files?.[0];
            if (!file) return;

            const text = await file.text();
            const extension = file.name.split('.').pop()?.toLowerCase() || '';

            // Determine format
            let format: InstanceFormat;
            switch (extension) {
                case 'csv': format = 'csv'; break;
                case 'json': format = 'json'; break;
                case 'xml': format = 'xml'; break;
                case 'yaml':
                case 'yml': format = 'yaml'; break;
                default: format = 'json';
            }

            const activeTab = this.state.tabs[this.state.activeTabIndex];

            // Update state
            this.setState({
                tabs: this.state.tabs.map((tab, idx) =>
                    idx === this.state.activeTabIndex
                        ? { ...tab, instanceContent: text, instanceFormat: format }
                        : tab
                )
            });

            // Register with LSP
            await this.registerInputWithLSP(activeTab.name, text, format);

            // Fire event for other widgets
            this.fireInputInstanceContentChanged(activeTab.name, text, format);
        };

        input.click();
    }
}
```

#### Step 5: Configure Monaco Completion Triggers

**File**: `/theia-extension/utlx-theia-extension/src/browser/editor/utlx-editor-widget.tsx`

Ensure Monaco triggers completion on `$` and `.`:

```typescript
protected override onAfterAttach(msg: Message): void {
    super.onAfterAttach(msg);

    // ... existing setup

    this.editor = monaco.editor.create(this.editorContainer, {
        model: model,
        language: 'utlx',  // Change from 'plaintext' to 'utlx'
        theme: 'vs-dark',
        automaticLayout: true,
        suggestOnTriggerCharacters: true,  // Enable trigger characters
        // ...
    });

    // Register UTLX language if not already registered
    this.registerUTLXLanguage();
}

private registerUTLXLanguage(): void {
    // Check if already registered
    const languages = monaco.languages.getLanguages();
    if (languages.some(lang => lang.id === 'utlx')) {
        return;
    }

    // Register language
    monaco.languages.register({ id: 'utlx' });

    // Set language configuration
    monaco.languages.setLanguageConfiguration('utlx', {
        wordPattern: /(-?\d*\.\d\w*)|([^\`\~\!\@\#\%\^\&\*\(\)\-\=\+\[\{\]\}\\\|\;\:\'\"\,\.\<\>\/\?\s]+)/g,
        comments: {
            lineComment: '//',
            blockComment: ['/*', '*/']
        },
        brackets: [
            ['{', '}'],
            ['[', ']'],
            ['(', ')']
        ],
        autoClosingPairs: [
            { open: '{', close: '}' },
            { open: '[', close: ']' },
            { open: '(', close: ')' },
            { open: '"', close: '"' },
            { open: "'", close: "'" }
        ]
    });

    // Register completion provider with trigger characters
    monaco.languages.registerCompletionItemProvider('utlx', {
        triggerCharacters: ['$', '.'],
        provideCompletionItems: async (model, position, context, token) => {
            // This will trigger LSP textDocument/completion
            // LSP client handles the actual request to daemon
            return { suggestions: [] };  // LSP client will populate
        }
    });
}
```

---

### Phase 2: Schema-Based Completion (Future Enhancement)

Extend CompletionService to check document mode:

```kotlin
fun getCompletions(params: CompletionParams): CompletionList {
    val uri = params.textDocument.uri
    val mode = stateManager.getDocumentMode(uri) ?: DocumentMode.RUNTIME

    return when (mode) {
        DocumentMode.DESIGN_TIME -> {
            // Use schema-based completion (already exists)
            val typeEnv = stateManager.getTypeEnvironment(uri)
            if (typeEnv != null) {
                PathCompleter(typeEnv).complete(partialPath)
            } else {
                CompletionList(false, emptyList())
            }
        }
        DocumentMode.RUNTIME -> {
            // Use actual input data (Phase 1 implementation)
            completeFromInputData(uri, partialPath)
        }
    }
}
```

---

## Files to Modify Summary

### Phase 1 (Required):

1. **DaemonServer.kt** (~50 lines)
   - Add `handleRegisterInputData()` method
   - Add `parseContentToUDM()` helper

2. **StateManager.kt** (~40 lines)
   - Add `inputDataCache` field
   - Add `registerInputData()`, `getInputFields()`, `getAllInputs()`, `getInputNames()` methods
   - Update `clearState()` to clear input cache

3. **CompletionService.kt** (~60 lines)
   - Add input access pattern detection
   - Add `buildFieldCompletions()` method
   - Add `buildInputNameCompletions()` method

4. **multi-input-panel-widget.tsx** (~40 lines)
   - Add `registerInputWithLSP()` method
   - Add `getCurrentDocumentUri()` helper
   - Update `handleLoadFile()` to call LSP registration

5. **utlx-editor-widget.tsx** (~30 lines)
   - Add `registerUTLXLanguage()` method
   - Configure trigger characters

### Phase 2 (Future):

6. **CompletionService.kt** (~20 lines)
   - Add mode-based routing

---

## Testing Plan

### Unit Tests:

1. **InputMetadataExtractor** (already exists)
   - Test field extraction from CSV
   - Test field extraction from JSON
   - Test field extraction from XML

2. **StateManager**
   - Test `registerInputData()`
   - Test `getInputFields()`
   - Test `getAllInputs()`
   - Test cache clearing

3. **CompletionService**
   - Test `$` completion (list inputs)
   - Test `$input.` completion (list fields)
   - Test field name filtering

### Integration Tests:

1. **End-to-End**
   - Load CSV in multi-input widget
   - Verify LSP registration
   - Type `$` in editor, verify input list
   - Type `$employees.`, verify field list
   - Verify field names match CSV headers

2. **Multiple Inputs**
   - Load 2+ inputs
   - Verify each input registered separately
   - Verify correct fields for each input

---

## Performance Considerations

### Memory:

- **UDM structures**: Only store InputMetadata (field list), not full UDM
- **Typical overhead**: ~100-500 bytes per input (field names + metadata)
- **Large files**: 1000-field CSV = ~50KB metadata

### Parsing:

- **When**: Only when input loaded/changed (not on every keystroke)
- **Cost**: CSV with 10,000 rows + 50 columns ≈ 50-200ms parse time
- **Mitigation**: Parse in background, don't block UI

### Caching:

- **Cache lifetime**: Until input changed or document closed
- **Invalidation**: Clear on input reload/modification
- **Sharing**: Multiple documents can reference same input (future optimization)

---

## Alternatives Considered

### Alternative 1: Parse in Frontend (Widget)
**Rejected**: Would require duplicating format parsers in TypeScript

### Alternative 2: Stream Large Files
**Deferred**: Implement if performance issues arise with large files

### Alternative 3: Use Worker Threads for Parsing
**Deferred**: Kotlin coroutines sufficient for now

---

## Future Enhancements

### 1. Deep Path Completion
Currently: `$input.` → show top-level fields
Future: `$input.Order.` → show nested fields in Order object

### 2. Type-Aware Suggestions
Show field type alongside name: `Department (String)`, `Salary (Number)`

### 3. Smart Filtering
Filter suggestions based on:
- Recently used fields
- Frequently used fields
- Context (inside filter vs map)

### 4. Preview Values
Show sample values in completion detail:
```
Department
  String (5 values)
  Sample: "Engineering", "Sales", "Marketing"
```

### 5. Cross-Input Joins
When user types `$input1.` inside context of `$input2`, suggest join patterns

---

## Success Metrics

### Phase 1 Success Criteria:

- ✅ User types `$` → sees list of registered inputs
- ✅ User types `$employees.` → sees CSV header names
- ✅ Field suggestions are accurate (match actual data)
- ✅ Works with CSV, JSON, XML formats
- ✅ Multiple inputs handled correctly
- ✅ Performance: Parse < 200ms for typical files
- ✅ Memory: < 1MB overhead for 10 inputs

### Phase 2 Success Criteria:

- ✅ Schema-based completion in DESIGN_TIME mode
- ✅ Data-based completion in RUNTIME mode
- ✅ Seamless switching between modes

---

## References

### Existing Code:

- **UDM Definition**: `/modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt`
- **InputMetadataExtractor**: `/modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/InputMetadataExtractor.kt`
- **LSP Daemon**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt`
- **StateManager**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/state/StateManager.kt`
- **CompletionService**: `/modules/daemon/src/main/kotlin/org/apache/utlx/daemon/completion/CompletionService.kt`
- **Multi-Input Widget**: `/theia-extension/utlx-theia-extension/src/browser/input-panel/multi-input-panel-widget.tsx`
- **Editor Widget**: `/theia-extension/utlx-theia-extension/src/browser/editor/utlx-editor-widget.tsx`

### Related Documentation:

- **Error Enhancer Integration**: `/docs/gen-ai/error-enhancer-integration.md`
- **LSP Diagnostics Schema**: `/docs/gen-ai/lsp-diagnostics-schema.json`

---

## Conclusion

The **Hybrid Approach** with **Phase 1 priority on runtime data-based completion** provides the best balance of:

- ✅ Meeting immediate user needs (RUNTIME mode with actual data)
- ✅ Accurate suggestions based on real input structure
- ✅ Leveraging existing infrastructure (InputMetadataExtractor)
- ✅ Extensible to schema-based design-time mode
- ✅ Clear implementation path with manageable complexity

The architecture is designed to be flexible, performant, and maintainable while providing an excellent developer experience in the UTLX editor.

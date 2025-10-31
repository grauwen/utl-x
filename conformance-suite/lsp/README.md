# LSP Conformance Suite

This suite tests the UTL-X Language Server Protocol (LSP) daemon implementation for compliance with LSP specification and correct behavior of language features.

## Structure

```
lsp/
├── tests/                      # Test cases organized by category
│   ├── protocol/               # LSP protocol compliance
│   │   ├── initialization/     # Server initialization
│   │   ├── lifecycle/          # Server lifecycle (shutdown, exit)
│   │   ├── json-rpc/           # JSON-RPC 2.0 compliance
│   │   └── transport/          # Transport layer (STDIO, Socket)
│   ├── document-sync/          # Document synchronization
│   ├── features/               # Language features
│   │   ├── completion/         # Autocomplete/completion
│   │   ├── hover/              # Hover information
│   │   └── diagnostics/        # Error/warning diagnostics
│   ├── workflows/              # Multi-step scenarios
│   └── edge-cases/             # Error handling, edge cases
├── runners/
│   └── kotlin-runner/          # Kotlin-based test runner
├── fixtures/
│   ├── schemas/                # Sample type definitions
│   └── documents/              # Sample UTL-X documents
└── lib/                        # Shared test utilities
```

## Test File Format

LSP tests are defined in YAML format with the following structure:

### Single Request/Response Test

```yaml
name: "initialize_basic"
description: "Basic server initialization"
category: "protocol/initialization"
tags: ["lsp", "initialization", "required"]

# Test sequence - array of steps
sequence:
  - type: request
    method: "initialize"
    params:
      processId: 12345
      rootUri: "file:///tmp/test"
      capabilities: {}

    # Expected response
    expect:
      result:
        capabilities:
          textDocumentSync:
            openClose: true
            change: 1
          completionProvider:
            triggerCharacters: [".", "$"]
          hoverProvider: true
        serverInfo:
          name: "UTL-X Language Server"
          version: "1.0.0-SNAPSHOT"
```

### Multi-Step Workflow Test

```yaml
name: "completion_workflow"
description: "Complete workflow: init → open doc → complete"
category: "workflows"
tags: ["completion", "workflow"]

# Document fixtures
documents:
  test_doc:
    uri: "file:///test.utlx"
    languageId: "utlx"
    version: 1
    text: |
      input: { name: "John", age: 30 }
      output: input.

sequence:
  # Step 1: Initialize
  - type: request
    method: "initialize"
    params:
      processId: 12345
      rootUri: "file:///tmp"
      capabilities: {}
    expect:
      result:
        capabilities:
          completionProvider: { }  # Has completion provider

  # Step 2: Open document
  - type: request
    method: "textDocument/didOpen"
    params:
      textDocument:
        uri: "{{documents.test_doc.uri}}"
        languageId: "{{documents.test_doc.languageId}}"
        version: "{{documents.test_doc.version}}"
        text: "{{documents.test_doc.text}}"
    expect:
      result: null  # Notification - no response

  # Step 3: Request completion
  - type: request
    method: "textDocument/completion"
    params:
      textDocument:
        uri: "{{documents.test_doc.uri}}"
      position:
        line: 1
        character: 14
      context:
        triggerKind: 2  # TriggerCharacter
        triggerCharacter: "."
    expect:
      result:
        isIncomplete: false
        items:
          - label: "name"
            kind: 10  # Property
          - label: "age"
            kind: 10  # Property
```

### Notification (Server → Client) Test

```yaml
name: "diagnostics_published"
description: "Server publishes diagnostics on document open"
category: "features/diagnostics"
tags: ["diagnostics", "notification"]

documents:
  invalid_doc:
    uri: "file:///invalid.utlx"
    languageId: "utlx"
    version: 1
    text: |
      input: { x: number }
      output: input.unknown_field

sequence:
  - type: request
    method: "initialize"
    params:
      processId: 12345
      rootUri: "file:///tmp"
      capabilities: {}

  - type: request
    method: "textDocument/didOpen"
    params:
      textDocument:
        uri: "{{documents.invalid_doc.uri}}"
        languageId: "{{documents.invalid_doc.languageId}}"
        version: "{{documents.invalid_doc.version}}"
        text: "{{documents.invalid_doc.text}}"

    # Expect server to send notification
    expect_notification:
      method: "textDocument/publishDiagnostics"
      params:
        uri: "{{documents.invalid_doc.uri}}"
        diagnostics:
          - severity: 1  # Error
            message: "Undefined property 'unknown_field'"
            range:
              start: { line: 1, character: 14 }
              end: { line: 1, character: 27 }
```

## Test Execution

The Kotlin-based test runner:

1. Starts the UTL-X daemon in STDIO mode
2. Loads test files from `tests/` directory
3. For each test:
   - Sends JSON-RPC requests in sequence
   - Validates responses match expectations
   - Captures server notifications
   - Verifies expected notifications received
4. Reports results (passed/failed/skipped)

## Running Tests

```bash
# Run all LSP tests
./lsp/runners/kotlin-runner/run-lsp-tests.sh

# Run specific category
./lsp/runners/kotlin-runner/run-lsp-tests.sh protocol/initialization

# Run single test
./lsp/runners/kotlin-runner/run-lsp-tests.sh protocol/initialization initialize_basic
```

## Test Categories

### Protocol Tests (Required for LSP Compliance)

- **Initialization**: `initialize` request handling
- **Lifecycle**: `shutdown` and `exit` handling
- **JSON-RPC**: Error codes, malformed requests, batch requests
- **Transport**: STDIO and Socket transports

### Document Sync Tests

- `textDocument/didOpen`
- `textDocument/didChange`
- `textDocument/didClose`
- Version tracking
- Full vs incremental sync

### Feature Tests

- **Completion**: Path completion, trigger characters, context-aware
- **Hover**: Type information, documentation, ranges
- **Diagnostics**: Error detection, warning levels, diagnostic ranges

### Workflow Tests

End-to-end scenarios combining multiple features:
- Open document → edit → complete → hover
- Multiple documents open
- Document close → reopen

### Edge Case Tests

- Malformed JSON-RPC
- Invalid parameters
- Unknown methods
- Server errors
- Timeout handling
- Large documents

## Expected Results

All tests should pass with 100% pass rate. Any failures indicate:
- LSP protocol non-compliance
- Daemon implementation bugs
- Regression in language features

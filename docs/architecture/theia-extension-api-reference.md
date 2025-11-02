# UTL-X Theia Extension API Reference

**Version:** 1.0.0
**Last Updated:** 2025-10-29
**Document Purpose:** Complete API reference for UTL-X Theia Extension developers

**Related Documents:**
- [theia-extension-design-with-design-time.md](theia-extension-design-with-design-time.md) - Design-time & runtime architecture (v2.0)
- [theia-implementation-roadmap.md](theia-implementation-roadmap.md) - Implementation status and phases
- [theia-extension-implementation-guide.md](theia-extension-implementation-guide.md) - Step-by-step implementation guide
- [theia-io-explained.md](theia-io-explained.md) - I/O communication details

**Note:** This document describes the API for runtime mode. For design-time mode features (schema-based type checking), see theia-extension-design-with-design-time.md.

---

## Table of Contents

1. [Overview](#overview)
2. [Backend API](#backend-api)
   - [UTLXService Interface](#utlxservice-interface)
   - [UTLXDaemonClient](#utlxdaemonclient)
   - [JSON-RPC Protocol](#json-rpc-protocol)
3. [Frontend API](#frontend-api)
   - [InputPanelWidget](#inputpanelwidget)
   - [OutputPanelWidget](#outputpanelwidget)
   - [TransformationExecutor](#transformationexecutor)
4. [Common Types](#common-types)
5. [Configuration](#configuration)
6. [Error Handling](#error-handling)
7. [Extension Points](#extension-points)
8. [Usage Examples](#usage-examples)

---

## Overview

The UTL-X Theia Extension provides a three-panel IDE for creating and testing UTL-X transformations. The architecture consists of:

```
┌──────────────────────────────────────────────────────────────┐
│                    Theia Frontend (Browser)                   │
│  ┌────────────┬──────────────────┬──────────────────────┐    │
│  │  Input     │   Editor with    │      Output          │    │
│  │  Panel     │   LSP Support    │      Panel           │    │
│  └────────────┴──────────────────┴──────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
                            ↕ WebSocket (JSON-RPC)
┌──────────────────────────────────────────────────────────────┐
│                    Theia Backend (Node.js)                    │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  UTLXService → UTLXDaemonClient → Child Process      │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
                            ↕ stdio (JSON-RPC)
┌──────────────────────────────────────────────────────────────┐
│                    UTL-X Daemon (JVM/Kotlin)                  │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  Parser → Validator → Type Checker → Executor        │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

**Key Protocols:**
- Frontend ↔ Backend: WebSocket with JSON-RPC over Theia's RPC framework
- Backend ↔ Daemon: stdio with custom JSON-RPC protocol

---

## Backend API

### UTLXService Interface

The main service interface for UTL-X operations. Implemented on the backend, proxied to frontend.

**Location:** `packages/common/src/protocol.ts`

```typescript
export const UTLX_SERVICE_PATH = '/services/utlx';

export interface UTLXService {
    /**
     * Parse UTL-X source code and return AST
     *
     * @param source - UTL-X source code
     * @param documentId - Optional document identifier for caching
     * @returns Parse result with AST or errors
     *
     * @example
     * const result = await utlxService.parse(sourceCode, 'file:///workspace/transform.utlx');
     */
    parse(source: string, documentId?: string): Promise<ParseResult>;

    /**
     * Validate UTL-X source code
     *
     * Performs full validation including:
     * - Syntax validation
     * - Type checking
     * - Lint checks
     * - Semantic analysis
     *
     * @param source - UTL-X source code
     * @returns Validation result with diagnostics
     *
     * @example
     * const result = await utlxService.validate(sourceCode);
     * if (result.diagnostics.length > 0) {
     *     console.error('Validation errors:', result.diagnostics);
     * }
     */
    validate(source: string): Promise<ValidationResult>;

    /**
     * Execute UTL-X transformation with inputs
     *
     * @param source - UTL-X source code
     * @param inputs - Array of input documents
     * @returns Execution result with output or errors
     *
     * @example
     * const result = await utlxService.execute(
     *     transformationSource,
     *     [
     *         { id: 'input1', name: 'customers.xml', content: xmlData, format: 'xml' },
     *         { id: 'input2', name: 'orders.json', content: jsonData, format: 'json' }
     *     ]
     * );
     */
    execute(
        source: string,
        inputs: InputDocument[]
    ): Promise<ExecutionResult>;

    /**
     * Get available standard library functions
     *
     * @returns List of standard library functions with documentation
     *
     * @example
     * const functions = await utlxService.getFunctions();
     * const mapFunction = functions.find(f => f.name === 'map');
     */
    getFunctions(): Promise<FunctionInfo[]>;

    /**
     * Get hover information for symbol at position
     *
     * @param source - UTL-X source code
     * @param position - Cursor position (line, column)
     * @returns Hover information (type, documentation)
     *
     * @example
     * const hover = await utlxService.getHover(source, { line: 10, column: 15 });
     * console.log(hover.contents); // "map(array: Array, fn: Function): Array"
     */
    getHover(
        source: string,
        position: Position
    ): Promise<HoverInfo | null>;

    /**
     * Get completion suggestions at position
     *
     * @param source - UTL-X source code
     * @param position - Cursor position
     * @returns Array of completion items
     *
     * @example
     * const completions = await utlxService.getCompletions(source, position);
     * // Returns: [{ label: 'map', kind: 'function', ... }, ...]
     */
    getCompletions(
        source: string,
        position: Position
    ): Promise<CompletionItem[]>;

    /**
     * Ping daemon to check if alive
     *
     * @returns true if daemon is responsive
     *
     * @example
     * const alive = await utlxService.ping();
     * if (!alive) {
     *     console.error('Daemon not responding');
     * }
     */
    ping(): Promise<boolean>;
}
```

---

### UTLXDaemonClient

Low-level client for communicating with UTL-X daemon process.

**Location:** `packages/backend/src/node/utlx-daemon-client.ts`

```typescript
export class UTLXDaemonClient extends EventEmitter {
    /**
     * Start the daemon process
     *
     * Spawns `utlx daemon --stdio` and establishes JSON-RPC communication.
     *
     * @throws Error if daemon binary not found
     * @throws Error if daemon fails to start within timeout
     *
     * @example
     * const client = new UTLXDaemonClient();
     * await client.start();
     */
    async start(): Promise<void>;

    /**
     * Stop the daemon process
     *
     * Gracefully shuts down daemon. Waits for pending requests to complete.
     *
     * @param timeout - Max time to wait for graceful shutdown (default: 5000ms)
     *
     * @example
     * await client.stop(10000); // Wait up to 10 seconds
     */
    async stop(timeout?: number): Promise<void>;

    /**
     * Check if daemon is running
     *
     * @returns true if daemon process exists and is responsive
     *
     * @example
     * if (!client.isRunning()) {
     *     await client.start();
     * }
     */
    isRunning(): boolean;

    /**
     * Send JSON-RPC request to daemon
     *
     * Low-level method for sending custom requests.
     *
     * @param method - JSON-RPC method name
     * @param params - Method parameters
     * @returns Response from daemon
     *
     * @throws Error if daemon not running
     * @throws Error if request times out
     *
     * @example
     * const result = await client.request('parse', {
     *     source: sourceCode,
     *     documentId: 'file:///test.utlx'
     * });
     */
    async request(method: string, params?: any): Promise<any>;

    /**
     * Send ping request to daemon
     *
     * @returns true if daemon responds within timeout
     *
     * @example
     * const alive = await client.ping();
     */
    async ping(): Promise<boolean>;

    /**
     * Parse UTL-X source
     *
     * @param source - UTL-X source code
     * @param documentId - Optional document ID for caching
     * @returns Parse result
     *
     * @example
     * const result = await client.parse(source);
     * if (result.errors.length > 0) {
     *     console.error('Parse errors:', result.errors);
     * }
     */
    async parse(source: string, documentId?: string): Promise<ParseResult>;

    /**
     * Validate UTL-X source
     *
     * @param source - UTL-X source code
     * @returns Validation result with diagnostics
     */
    async validate(source: string): Promise<ValidationResult>;

    /**
     * Execute UTL-X transformation
     *
     * @param source - UTL-X source code
     * @param inputs - Input documents
     * @returns Execution result
     */
    async execute(
        source: string,
        inputs: InputDocument[]
    ): Promise<ExecutionResult>;

    /**
     * Get standard library functions
     *
     * @returns Array of function metadata
     */
    async getFunctions(): Promise<FunctionInfo[]>;

    // Events

    /**
     * Emitted when daemon process starts
     *
     * @event
     * @example
     * client.on('started', () => {
     *     console.log('Daemon is ready');
     * });
     */
    on(event: 'started', listener: () => void): this;

    /**
     * Emitted when daemon process stops
     *
     * @event
     * @param code - Exit code
     * @param signal - Kill signal (if any)
     *
     * @example
     * client.on('stopped', (code, signal) => {
     *     console.log(`Daemon stopped: code=${code}, signal=${signal}`);
     * });
     */
    on(event: 'stopped', listener: (code: number | null, signal: string | null) => void): this;

    /**
     * Emitted when daemon encounters an error
     *
     * @event
     * @param error - Error object
     *
     * @example
     * client.on('error', (error) => {
     *     console.error('Daemon error:', error);
     * });
     */
    on(event: 'error', listener: (error: Error) => void): this;

    /**
     * Emitted when daemon writes to stderr
     *
     * @event
     * @param message - stderr output
     *
     * @example
     * client.on('stderr', (message) => {
     *     console.warn('Daemon stderr:', message);
     * });
     */
    on(event: 'stderr', listener: (message: string) => void): this;
}
```

---

### JSON-RPC Protocol

The daemon uses a custom JSON-RPC 2.0 protocol over stdio.

**Transport:** Newline-delimited JSON over stdin/stdout

#### Request Format

```typescript
interface JsonRpcRequest {
    jsonrpc: '2.0';
    id: number | string;
    method: string;
    params?: any;
}
```

#### Response Format

```typescript
interface JsonRpcResponse {
    jsonrpc: '2.0';
    id: number | string;
    result?: any;
    error?: JsonRpcError;
}

interface JsonRpcError {
    code: number;
    message: string;
    data?: any;
}
```

#### Methods

##### `ping`

Check daemon health.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "ping",
    "params": {}
}
```

**Response:**
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "pong": true,
        "timestamp": "2025-10-29T10:30:00Z"
    }
}
```

##### `parse`

Parse UTL-X source code.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "parse",
    "params": {
        "source": "%utlx 1.0\ninput json\noutput json\n---\n{ result: $input.data }",
        "documentId": "file:///workspace/transform.utlx"
    }
}
```

**Response (Success):**
```json
{
    "jsonrpc": "2.0",
    "id": 2,
    "result": {
        "ast": {
            "type": "TransformationRoot",
            "header": {
                "version": "1.0",
                "inputs": [{ "name": "input", "format": "json" }],
                "output": { "format": "json" }
            },
            "body": {
                "type": "ObjectConstruction",
                "properties": [...]
            }
        },
        "errors": []
    }
}
```

**Response (Parse Error):**
```json
{
    "jsonrpc": "2.0",
    "id": 2,
    "result": {
        "ast": null,
        "errors": [
            {
                "severity": "ERROR",
                "location": { "line": 5, "column": 10, "length": 5 },
                "message": "Unexpected token 'result'",
                "code": "E1002"
            }
        ]
    }
}
```

##### `validate`

Validate UTL-X source code.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "validate",
    "params": {
        "source": "%utlx 1.0\ninput json\noutput json\n---\n{ result: $input.data |> map(x => x * 2) }"
    }
}
```

**Response:**
```json
{
    "jsonrpc": "2.0",
    "id": 3,
    "result": {
        "valid": true,
        "diagnostics": [
            {
                "severity": "WARNING",
                "location": { "line": 5, "column": 35, "length": 10 },
                "message": "Consider using named lambda parameter for clarity",
                "code": "W2005",
                "tags": ["style"]
            }
        ]
    }
}
```

##### `execute`

Execute UTL-X transformation.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "execute",
    "params": {
        "source": "%utlx 1.0\ninput xml\noutput json\n---\n{ orders: $input.Orders.Order }",
        "inputs": [
            {
                "id": "input",
                "name": "orders.xml",
                "content": "<Orders><Order id=\"1\">...</Order></Orders>",
                "format": "xml"
            }
        ]
    }
}
```

**Response (Success):**
```json
{
    "jsonrpc": "2.0",
    "id": 4,
    "result": {
        "success": true,
        "output": "{\"orders\":[{\"id\":\"1\",...}]}",
        "format": "json",
        "executionTimeMs": 45
    }
}
```

**Response (Execution Error):**
```json
{
    "jsonrpc": "2.0",
    "id": 4,
    "result": {
        "success": false,
        "error": {
            "message": "Type mismatch: expected Array, got String",
            "location": { "line": 5, "column": 20 },
            "stackTrace": [...]
        }
    }
}
```

##### `getFunctions`

Get standard library function list.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "getFunctions",
    "params": {}
}
```

**Response:**
```json
{
    "jsonrpc": "2.0",
    "id": 5,
    "result": {
        "functions": [
            {
                "name": "map",
                "category": "Array",
                "signature": "map(array: Array, fn: Function): Array",
                "description": "Transform each element of an array using a function",
                "parameters": [
                    { "name": "array", "type": "Array", "description": "Input array" },
                    { "name": "fn", "type": "Function", "description": "Mapping function" }
                ],
                "returns": { "type": "Array", "description": "Transformed array" },
                "example": "map([1,2,3], x => x * 2) // [2,4,6]"
            },
            ...
        ]
    }
}
```

#### Error Codes

| Code | Category | Meaning |
|------|----------|---------|
| -32700 | JSON-RPC | Parse error (invalid JSON) |
| -32600 | JSON-RPC | Invalid request |
| -32601 | JSON-RPC | Method not found |
| -32602 | JSON-RPC | Invalid params |
| -32603 | JSON-RPC | Internal error |
| 1000-1999 | Parse | Syntax errors |
| 2000-2999 | Validation | Type errors, semantic errors |
| 3000-3999 | Execution | Runtime errors |
| 4000-4999 | System | I/O errors, resource errors |

---

## Frontend API

### InputPanelWidget

Widget for managing input documents.

**Location:** `packages/frontend/src/browser/input-panel-widget.tsx`

```typescript
@injectable()
export class InputPanelWidget extends ReactWidget {
    static readonly ID = 'utlx-input-panel';
    static readonly LABEL = 'Inputs';

    @inject(UTLXService)
    protected readonly utlxService!: UTLXService;

    @inject(WorkspaceService)
    protected readonly workspaceService!: WorkspaceService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    /**
     * Get all input documents
     *
     * @returns Array of input documents
     */
    getInputs(): InputDocument[];

    /**
     * Add a new input document
     *
     * @param input - Input document to add
     *
     * @example
     * widget.addInput({
     *     id: 'input1',
     *     name: 'customers.xml',
     *     content: xmlContent,
     *     format: 'xml'
     * });
     */
    addInput(input: InputDocument): void;

    /**
     * Remove input document by ID
     *
     * @param id - Input document ID
     *
     * @example
     * widget.removeInput('input1');
     */
    removeInput(id: string): void;

    /**
     * Update input document content
     *
     * @param id - Input document ID
     * @param content - New content
     *
     * @example
     * widget.updateInputContent('input1', newXmlContent);
     */
    updateInputContent(id: string, content: string): void;

    /**
     * Update input document format
     *
     * @param id - Input document ID
     * @param format - New format
     *
     * @example
     * widget.updateInputFormat('input1', 'json');
     */
    updateInputFormat(id: string, format: DataFormat): void;

    /**
     * Load input from file
     *
     * Opens file picker and loads selected file as input.
     * Format is auto-detected from file extension.
     *
     * @example
     * await widget.loadInputFromFile();
     */
    async loadInputFromFile(): Promise<void>;

    /**
     * Load input from workspace
     *
     * Shows quick pick of workspace files and loads selected file.
     *
     * @example
     * await widget.loadInputFromWorkspace();
     */
    async loadInputFromWorkspace(): Promise<void>;

    /**
     * Export input to file
     *
     * @param id - Input document ID
     *
     * @example
     * await widget.exportInput('input1');
     */
    async exportInput(id: string): Promise<void>;

    /**
     * Validate input document
     *
     * Validates that input content is well-formed for its format.
     *
     * @param id - Input document ID
     * @returns Validation result
     *
     * @example
     * const valid = await widget.validateInput('input1');
     * if (!valid) {
     *     console.error('Invalid input document');
     * }
     */
    async validateInput(id: string): Promise<boolean>;

    // Events

    /**
     * Emitted when inputs change
     *
     * @event
     * @example
     * widget.onInputsChanged((inputs) => {
     *     console.log(`Now have ${inputs.length} inputs`);
     * });
     */
    onInputsChanged(listener: (inputs: InputDocument[]) => void): Disposable;

    /**
     * Emitted when an input is selected
     *
     * @event
     * @example
     * widget.onInputSelected((input) => {
     *     console.log(`Selected input: ${input.name}`);
     * });
     */
    onInputSelected(listener: (input: InputDocument) => void): Disposable;
}
```

---

### OutputPanelWidget

Widget for displaying transformation output.

**Location:** `packages/frontend/src/browser/output-panel-widget.tsx`

```typescript
@injectable()
export class OutputPanelWidget extends ReactWidget {
    static readonly ID = 'utlx-output-panel';
    static readonly LABEL = 'Output';

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    /**
     * Get current output
     *
     * @returns Current output or null if no execution
     */
    getOutput(): ExecutionResult | null;

    /**
     * Set output from execution result
     *
     * @param result - Execution result
     *
     * @example
     * widget.setOutput({
     *     success: true,
     *     output: '{"result": [1,2,3]}',
     *     format: 'json',
     *     executionTimeMs: 45
     * });
     */
    setOutput(result: ExecutionResult): void;

    /**
     * Clear output
     *
     * @example
     * widget.clear();
     */
    clear(): void;

    /**
     * Get current output format
     *
     * @returns Output format ('json', 'xml', 'csv', etc.)
     */
    getFormat(): DataFormat | null;

    /**
     * Set output view mode
     *
     * @param mode - View mode ('pretty', 'raw', 'tree')
     *
     * @example
     * widget.setViewMode('tree'); // Show JSON as tree view
     */
    setViewMode(mode: 'pretty' | 'raw' | 'tree'): void;

    /**
     * Export output to file
     *
     * Opens save dialog and exports output to selected file.
     *
     * @example
     * await widget.exportOutput();
     */
    async exportOutput(): Promise<void>;

    /**
     * Copy output to clipboard
     *
     * @example
     * widget.copyToClipboard();
     */
    copyToClipboard(): void;

    /**
     * Get execution statistics
     *
     * @returns Execution stats (time, memory, etc.)
     *
     * @example
     * const stats = widget.getStats();
     * console.log(`Execution took ${stats.executionTimeMs}ms`);
     */
    getStats(): ExecutionStats | null;

    // Events

    /**
     * Emitted when output changes
     *
     * @event
     * @example
     * widget.onOutputChanged((result) => {
     *     if (result.success) {
     *         console.log('Transformation succeeded');
     *     }
     * });
     */
    onOutputChanged(listener: (result: ExecutionResult | null) => void): Disposable;

    /**
     * Emitted when view mode changes
     *
     * @event
     */
    onViewModeChanged(listener: (mode: string) => void): Disposable;
}
```

---

### TransformationExecutor

Service for executing transformations with input coordination.

**Location:** `packages/frontend/src/browser/transformation-executor.ts`

```typescript
@injectable()
export class TransformationExecutor {
    @inject(UTLXService)
    protected readonly utlxService!: UTLXService;

    @inject(EditorManager)
    protected readonly editorManager!: EditorManager;

    @inject(InputPanelWidget)
    protected readonly inputPanel!: InputPanelWidget;

    @inject(OutputPanelWidget)
    protected readonly outputPanel!: OutputPanelWidget;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    /**
     * Execute transformation with current inputs
     *
     * Reads transformation from active editor, gets inputs from input panel,
     * executes transformation, and displays output in output panel.
     *
     * @returns Execution result
     *
     * @example
     * const result = await executor.execute();
     * if (result.success) {
     *     console.log('Transformation succeeded');
     * }
     */
    async execute(): Promise<ExecutionResult>;

    /**
     * Execute transformation with specific inputs
     *
     * @param source - UTL-X source code
     * @param inputs - Input documents
     * @returns Execution result
     *
     * @example
     * const result = await executor.executeWithInputs(
     *     transformSource,
     *     [input1, input2]
     * );
     */
    async executeWithInputs(
        source: string,
        inputs: InputDocument[]
    ): Promise<ExecutionResult>;

    /**
     * Validate transformation before execution
     *
     * @param source - UTL-X source code
     * @returns true if valid
     *
     * @example
     * if (await executor.validate(source)) {
     *     await executor.execute();
     * }
     */
    async validate(source: string): Promise<boolean>;

    /**
     * Enable auto-execution on changes
     *
     * When enabled, transformation executes automatically when:
     * - Editor content changes (debounced)
     * - Input documents change
     *
     * @param enabled - Enable or disable auto-execution
     * @param debounceMs - Debounce delay in milliseconds (default: 500)
     *
     * @example
     * executor.setAutoExecute(true, 1000); // Auto-execute after 1s of inactivity
     */
    setAutoExecute(enabled: boolean, debounceMs?: number): void;

    /**
     * Check if auto-execution is enabled
     *
     * @returns true if auto-execution is enabled
     */
    isAutoExecuteEnabled(): boolean;

    /**
     * Cancel ongoing execution
     *
     * @example
     * if (executor.isExecuting()) {
     *     executor.cancel();
     * }
     */
    cancel(): void;

    /**
     * Check if execution is in progress
     *
     * @returns true if executing
     */
    isExecuting(): boolean;

    // Events

    /**
     * Emitted when execution starts
     *
     * @event
     */
    onExecutionStarted(listener: () => void): Disposable;

    /**
     * Emitted when execution completes
     *
     * @event
     */
    onExecutionCompleted(listener: (result: ExecutionResult) => void): Disposable;

    /**
     * Emitted when execution fails
     *
     * @event
     */
    onExecutionFailed(listener: (error: Error) => void): Disposable;

    /**
     * Emitted when execution is cancelled
     *
     * @event
     */
    onExecutionCancelled(listener: () => void): Disposable;
}
```

---

## Common Types

### Data Structures

**Location:** `packages/common/src/types.ts`

```typescript
/**
 * Supported data formats
 */
export type DataFormat = 'xml' | 'json' | 'csv' | 'yaml' | 'auto';

/**
 * Input document for transformation
 */
export interface InputDocument {
    /** Unique identifier (e.g., 'input1', 'customers') */
    id: string;

    /** Display name (e.g., 'customers.xml') */
    name: string;

    /** Document content as string */
    content: string;

    /** Format of the content */
    format: DataFormat;

    /** Optional encoding (default: 'UTF-8') */
    encoding?: string;
}

/**
 * Source code position
 */
export interface Position {
    /** Line number (0-indexed) */
    line: number;

    /** Column number (0-indexed) */
    column: number;
}

/**
 * Source code range
 */
export interface Range {
    /** Start position */
    start: Position;

    /** End position */
    end: Position;
}

/**
 * Source code location
 */
export interface Location {
    /** Line number (1-indexed for display) */
    line: number;

    /** Column number (1-indexed for display) */
    column: number;

    /** Length in characters */
    length: number;

    /** Optional file URI */
    uri?: string;
}

/**
 * Diagnostic severity
 */
export enum DiagnosticSeverity {
    ERROR = 'ERROR',
    WARNING = 'WARNING',
    INFO = 'INFO',
    HINT = 'HINT'
}

/**
 * Diagnostic tag
 */
export enum DiagnosticTag {
    DEPRECATED = 'deprecated',
    UNNECESSARY = 'unnecessary',
    STYLE = 'style',
    PERFORMANCE = 'performance',
    SECURITY = 'security'
}

/**
 * Diagnostic message
 */
export interface Diagnostic {
    /** Severity level */
    severity: DiagnosticSeverity;

    /** Location in source code */
    location: Location;

    /** Error message */
    message: string;

    /** Optional error code (e.g., 'E1002') */
    code?: string;

    /** Detailed explanation */
    explanation?: string;

    /** Related locations (e.g., where variable was declared) */
    relatedLocations?: RelatedLocation[];

    /** Quick fixes */
    fixes?: QuickFix[];

    /** Tags */
    tags?: DiagnosticTag[];
}

/**
 * Related location for diagnostics
 */
export interface RelatedLocation {
    location: Location;
    message: string;
}

/**
 * Quick fix for diagnostic
 */
export interface QuickFix {
    /** Fix title (e.g., 'Add missing import') */
    title: string;

    /** Text edits to apply */
    edits: TextEdit[];
}

/**
 * Text edit
 */
export interface TextEdit {
    /** Range to replace */
    range: Range;

    /** New text */
    newText: string;
}

/**
 * Parse result
 */
export interface ParseResult {
    /** Abstract Syntax Tree (null if parse failed) */
    ast: any | null;

    /** Parse errors */
    errors: Diagnostic[];
}

/**
 * Validation result
 */
export interface ValidationResult {
    /** Whether validation passed (no errors) */
    valid: boolean;

    /** All diagnostics (errors, warnings, hints) */
    diagnostics: Diagnostic[];
}

/**
 * Execution result
 */
export interface ExecutionResult {
    /** Whether execution succeeded */
    success: boolean;

    /** Output content (if successful) */
    output?: string;

    /** Output format */
    format?: DataFormat;

    /** Execution time in milliseconds */
    executionTimeMs?: number;

    /** Error details (if failed) */
    error?: ExecutionError;
}

/**
 * Execution error
 */
export interface ExecutionError {
    /** Error message */
    message: string;

    /** Location where error occurred */
    location?: Location;

    /** Stack trace */
    stackTrace?: string[];
}

/**
 * Execution statistics
 */
export interface ExecutionStats {
    /** Execution time in milliseconds */
    executionTimeMs: number;

    /** Memory used in bytes */
    memoryBytes?: number;

    /** Number of nodes processed */
    nodesProcessed?: number;
}

/**
 * Function information
 */
export interface FunctionInfo {
    /** Function name */
    name: string;

    /** Category (e.g., 'Array', 'String', 'Math') */
    category: string;

    /** Full signature */
    signature: string;

    /** Description */
    description: string;

    /** Parameters */
    parameters: ParameterInfo[];

    /** Return type */
    returns: ReturnInfo;

    /** Example usage */
    example?: string;

    /** Additional notes */
    notes?: string;
}

/**
 * Parameter information
 */
export interface ParameterInfo {
    /** Parameter name */
    name: string;

    /** Type */
    type: string;

    /** Description */
    description: string;

    /** Whether optional */
    optional?: boolean;

    /** Default value */
    defaultValue?: string;
}

/**
 * Return information
 */
export interface ReturnInfo {
    /** Return type */
    type: string;

    /** Description */
    description: string;
}

/**
 * Hover information
 */
export interface HoverInfo {
    /** Hover contents (markdown supported) */
    contents: string;

    /** Range to highlight */
    range?: Range;
}

/**
 * Completion item kind
 */
export enum CompletionItemKind {
    FUNCTION = 'function',
    VARIABLE = 'variable',
    KEYWORD = 'keyword',
    SNIPPET = 'snippet',
    PROPERTY = 'property'
}

/**
 * Completion item
 */
export interface CompletionItem {
    /** Label (displayed in list) */
    label: string;

    /** Kind */
    kind: CompletionItemKind;

    /** Text to insert */
    insertText: string;

    /** Documentation */
    documentation?: string;

    /** Detail (type signature) */
    detail?: string;

    /** Sort priority (lower = higher in list) */
    sortText?: string;
}
```

---

## Configuration

### Extension Configuration

**Location:** `package.json` (contributes section)

```json
{
    "contributes": {
        "configuration": {
            "title": "UTL-X",
            "properties": {
                "utlx.daemonPath": {
                    "type": "string",
                    "default": "utlx",
                    "description": "Path to UTL-X CLI binary"
                },
                "utlx.daemonTimeout": {
                    "type": "number",
                    "default": 5000,
                    "description": "Daemon startup timeout in milliseconds"
                },
                "utlx.autoExecute": {
                    "type": "boolean",
                    "default": false,
                    "description": "Enable auto-execution on changes"
                },
                "utlx.autoExecuteDelay": {
                    "type": "number",
                    "default": 500,
                    "description": "Delay before auto-execution in milliseconds"
                },
                "utlx.validation.enabled": {
                    "type": "boolean",
                    "default": true,
                    "description": "Enable validation"
                },
                "utlx.validation.lintLevel": {
                    "type": "string",
                    "enum": ["error", "warning", "info", "hint"],
                    "default": "warning",
                    "description": "Minimum severity level for lint diagnostics"
                },
                "utlx.editor.formatOnSave": {
                    "type": "boolean",
                    "default": false,
                    "description": "Format document on save"
                },
                "utlx.output.defaultFormat": {
                    "type": "string",
                    "enum": ["pretty", "raw", "tree"],
                    "default": "pretty",
                    "description": "Default output view format"
                },
                "utlx.output.maxSize": {
                    "type": "number",
                    "default": 10485760,
                    "description": "Maximum output size in bytes (default: 10MB)"
                }
            }
        }
    }
}
```

### Accessing Configuration

```typescript
import { PreferenceService } from '@theia/core/lib/browser';

@injectable()
export class MyService {
    @inject(PreferenceService)
    protected readonly preferences!: PreferenceService;

    getDaemonPath(): string {
        return this.preferences.get('utlx.daemonPath', 'utlx');
    }

    isAutoExecuteEnabled(): boolean {
        return this.preferences.get('utlx.autoExecute', false);
    }

    // Listen for changes
    watchAutoExecute(callback: (enabled: boolean) => void): Disposable {
        return this.preferences.onPreferenceChanged(event => {
            if (event.preferenceName === 'utlx.autoExecute') {
                callback(event.newValue as boolean);
            }
        });
    }
}
```

---

## Error Handling

### Error Types

```typescript
/**
 * Base error class for UTL-X errors
 */
export class UTLXError extends Error {
    constructor(
        message: string,
        public readonly code?: string,
        public readonly location?: Location
    ) {
        super(message);
        this.name = 'UTLXError';
    }
}

/**
 * Daemon communication error
 */
export class DaemonError extends UTLXError {
    constructor(message: string, code?: string) {
        super(message, code);
        this.name = 'DaemonError';
    }
}

/**
 * Parse error
 */
export class ParseError extends UTLXError {
    constructor(message: string, location?: Location) {
        super(message, undefined, location);
        this.name = 'ParseError';
    }
}

/**
 * Validation error
 */
export class ValidationError extends UTLXError {
    constructor(
        message: string,
        public readonly diagnostics: Diagnostic[]
    ) {
        super(message);
        this.name = 'ValidationError';
    }
}

/**
 * Execution error
 */
export class ExecutionError extends UTLXError {
    constructor(
        message: string,
        location?: Location,
        public readonly stackTrace?: string[]
    ) {
        super(message, undefined, location);
        this.name = 'ExecutionError';
    }
}
```

### Error Handling Patterns

```typescript
// Backend: Daemon communication
try {
    const result = await this.daemonClient.execute(source, inputs);
    return result;
} catch (error) {
    if (error instanceof DaemonError) {
        // Daemon not responding - try to restart
        this.logger.error('Daemon error:', error);
        await this.restartDaemon();
        throw error;
    } else {
        // Unexpected error
        this.logger.error('Unexpected error:', error);
        throw new UTLXError('Internal error', 'E9999');
    }
}

// Frontend: User-facing errors
try {
    const result = await this.utlxService.execute(source, inputs);
    this.outputPanel.setOutput(result);
} catch (error) {
    if (error instanceof ValidationError) {
        this.messageService.error(
            `Validation failed: ${error.diagnostics.length} error(s)`
        );
        // Show diagnostics in editor
        this.showDiagnostics(error.diagnostics);
    } else if (error instanceof ExecutionError) {
        this.messageService.error(`Execution failed: ${error.message}`);
        if (error.location) {
            // Jump to error location
            this.revealLocation(error.location);
        }
    } else {
        this.messageService.error('Unexpected error occurred');
        console.error(error);
    }
}
```

---

## Extension Points

### Custom Input Providers

Create custom input providers to load data from external sources.

```typescript
/**
 * Input provider interface
 */
export interface InputProvider {
    /** Provider ID (unique) */
    readonly id: string;

    /** Display name */
    readonly label: string;

    /** Provider icon (optional) */
    readonly icon?: string;

    /**
     * Check if this provider can load from given URI
     */
    canProvideInput(uri: string): boolean;

    /**
     * Load input from URI
     */
    loadInput(uri: string): Promise<InputDocument>;
}

// Example: HTTP Input Provider
@injectable()
export class HttpInputProvider implements InputProvider {
    readonly id = 'http';
    readonly label = 'HTTP';
    readonly icon = 'cloud-download';

    canProvideInput(uri: string): boolean {
        return uri.startsWith('http://') || uri.startsWith('https://');
    }

    async loadInput(uri: string): Promise<InputDocument> {
        const response = await fetch(uri);
        const content = await response.text();
        const format = this.detectFormat(response.headers.get('content-type'));

        return {
            id: `http_${Date.now()}`,
            name: uri.split('/').pop() || 'http-input',
            content,
            format
        };
    }

    private detectFormat(contentType: string | null): DataFormat {
        if (!contentType) return 'auto';
        if (contentType.includes('xml')) return 'xml';
        if (contentType.includes('json')) return 'json';
        if (contentType.includes('csv')) return 'csv';
        if (contentType.includes('yaml')) return 'yaml';
        return 'auto';
    }
}

// Register provider
bind(InputProvider).to(HttpInputProvider).inSingletonScope();
bindContributionProvider(bind, InputProvider);
```

### Custom Output Renderers

Create custom renderers for specific output formats.

```typescript
/**
 * Output renderer interface
 */
export interface OutputRenderer {
    /** Supported formats */
    readonly supportedFormats: DataFormat[];

    /**
     * Check if this renderer can render given format
     */
    canRender(format: DataFormat): boolean;

    /**
     * Render output
     */
    render(content: string, format: DataFormat): React.ReactElement;
}

// Example: XML Tree Renderer
@injectable()
export class XmlTreeRenderer implements OutputRenderer {
    readonly supportedFormats: DataFormat[] = ['xml'];

    canRender(format: DataFormat): boolean {
        return format === 'xml';
    }

    render(content: string, format: DataFormat): React.ReactElement {
        const parsed = new DOMParser().parseFromString(content, 'text/xml');
        return <XmlTreeView document={parsed} />;
    }
}

// Register renderer
bind(OutputRenderer).to(XmlTreeRenderer).inSingletonScope();
bindContributionProvider(bind, OutputRenderer);
```

---

## Usage Examples

### Example 1: Basic Transformation Execution

```typescript
import { injectable, inject } from '@theia/core/shared/inversify';
import { UTLXService, InputDocument, ExecutionResult } from '@utlx/common';

@injectable()
export class TransformationService {
    @inject(UTLXService)
    protected readonly utlxService!: UTLXService;

    async transformOrders(xmlData: string): Promise<string> {
        // Define transformation
        const source = `
%utlx 1.0
input xml
output json
---
{
  orders: $input.Orders.Order |> map(order => {
    id: order.@id,
    customer: order.Customer.Name,
    total: sum(order.Items.Item.(@price * @quantity))
  })
}
        `.trim();

        // Prepare input
        const inputs: InputDocument[] = [
            {
                id: 'input',
                name: 'orders.xml',
                content: xmlData,
                format: 'xml'
            }
        ];

        // Execute transformation
        const result: ExecutionResult = await this.utlxService.execute(source, inputs);

        if (!result.success) {
            throw new Error(`Transformation failed: ${result.error?.message}`);
        }

        return result.output!;
    }
}
```

### Example 2: Multi-Input Transformation

```typescript
async transformWithMultipleInputs(
    customersXml: string,
    ordersJson: string
): Promise<string> {
    const source = `
%utlx 1.0
input: customers xml, orders json
output json
---
{
  report: {
    customers: $customers.Customers.Customer |> map(c => {
      id: c.@id,
      name: c.Name,
      orders: $orders.orders |> filter(o => o.customerId == c.@id)
    })
  }
}
    `.trim();

    const inputs: InputDocument[] = [
        { id: 'customers', name: 'customers.xml', content: customersXml, format: 'xml' },
        { id: 'orders', name: 'orders.json', content: ordersJson, format: 'json' }
    ];

    const result = await this.utlxService.execute(source, inputs);
    return result.output!;
}
```

### Example 3: Real-time Validation

```typescript
import { EditorManager } from '@theia/editor/lib/browser';
import { ValidationResult, Diagnostic } from '@utlx/common';

@injectable()
export class ValidationService {
    @inject(EditorManager)
    protected readonly editorManager!: EditorManager;

    @inject(UTLXService)
    protected readonly utlxService!: UTLXService;

    private validationTimeout: NodeJS.Timeout | undefined;

    async startValidation(): Promise<void> {
        const editor = this.editorManager.currentEditor;
        if (!editor) return;

        // Listen for document changes
        editor.editor.document.onContentChanged(() => {
            this.scheduleValidation();
        });
    }

    private scheduleValidation(): void {
        // Debounce validation
        if (this.validationTimeout) {
            clearTimeout(this.validationTimeout);
        }

        this.validationTimeout = setTimeout(async () => {
            await this.validateCurrentDocument();
        }, 500);
    }

    private async validateCurrentDocument(): Promise<void> {
        const editor = this.editorManager.currentEditor;
        if (!editor) return;

        const source = editor.editor.document.getText();
        const result: ValidationResult = await this.utlxService.validate(source);

        // Clear existing diagnostics
        this.clearDiagnostics(editor.editor.uri);

        // Add new diagnostics
        if (result.diagnostics.length > 0) {
            this.showDiagnostics(editor.editor.uri, result.diagnostics);
        }
    }

    private showDiagnostics(uri: string, diagnostics: Diagnostic[]): void {
        // Convert to Monaco/LSP diagnostics and display
        // Implementation details omitted for brevity
    }

    private clearDiagnostics(uri: string): void {
        // Clear diagnostics for document
    }
}
```

### Example 4: Auto-Execution on Input Change

```typescript
@injectable()
export class AutoExecutionService {
    @inject(TransformationExecutor)
    protected readonly executor!: TransformationExecutor;

    @inject(InputPanelWidget)
    protected readonly inputPanel!: InputPanelWidget;

    @inject(PreferenceService)
    protected readonly preferences!: PreferenceService;

    start(): void {
        // Watch for input changes
        this.inputPanel.onInputsChanged(async (inputs) => {
            if (this.isAutoExecuteEnabled()) {
                await this.executor.execute();
            }
        });

        // Watch for editor changes
        this.editorManager.onCurrentEditorChanged(async () => {
            if (this.isAutoExecuteEnabled()) {
                await this.scheduleExecution();
            }
        });

        // Watch for preference changes
        this.preferences.onPreferenceChanged(event => {
            if (event.preferenceName === 'utlx.autoExecute') {
                this.executor.setAutoExecute(
                    event.newValue as boolean,
                    this.getAutoExecuteDelay()
                );
            }
        });
    }

    private isAutoExecuteEnabled(): boolean {
        return this.preferences.get('utlx.autoExecute', false);
    }

    private getAutoExecuteDelay(): number {
        return this.preferences.get('utlx.autoExecuteDelay', 500);
    }

    private async scheduleExecution(): Promise<void> {
        // Debounced execution
        await this.executor.execute();
    }
}
```

### Example 5: Custom Command - Export All Inputs

```typescript
import { Command, CommandContribution, CommandRegistry } from '@theia/core';
import { inject, injectable } from '@theia/core/shared/inversify';

export const EXPORT_ALL_INPUTS: Command = {
    id: 'utlx.exportAllInputs',
    label: 'UTL-X: Export All Inputs'
};

@injectable()
export class UTLXCommandContribution implements CommandContribution {
    @inject(InputPanelWidget)
    protected readonly inputPanel!: InputPanelWidget;

    @inject(WorkspaceService)
    protected readonly workspaceService!: WorkspaceService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    registerCommands(commands: CommandRegistry): void {
        commands.registerCommand(EXPORT_ALL_INPUTS, {
            execute: async () => {
                await this.exportAllInputs();
            }
        });
    }

    private async exportAllInputs(): Promise<void> {
        const inputs = this.inputPanel.getInputs();
        if (inputs.length === 0) {
            this.messageService.warn('No inputs to export');
            return;
        }

        const workspaceRoot = this.workspaceService.workspace;
        if (!workspaceRoot) {
            this.messageService.error('No workspace open');
            return;
        }

        // Export each input to workspace
        for (const input of inputs) {
            const filePath = `${workspaceRoot.resource.path}/inputs/${input.name}`;
            await this.fileService.write(
                URI.parse(filePath),
                input.content
            );
        }

        this.messageService.info(`Exported ${inputs.length} input(s) to workspace`);
    }
}
```

### Example 6: Custom Input Provider - Database Connection

```typescript
@injectable()
export class DatabaseInputProvider implements InputProvider {
    readonly id = 'database';
    readonly label = 'Database Query';
    readonly icon = 'database';

    @inject(DatabaseService)
    protected readonly dbService!: DatabaseService;

    canProvideInput(uri: string): boolean {
        return uri.startsWith('db://');
    }

    async loadInput(uri: string): Promise<InputDocument> {
        // Parse URI: db://connection/table?query=SELECT * FROM orders
        const parsed = new URL(uri);
        const connection = parsed.hostname;
        const table = parsed.pathname.substring(1);
        const query = parsed.searchParams.get('query') || `SELECT * FROM ${table}`;

        // Execute query
        const results = await this.dbService.query(connection, query);

        // Convert results to JSON
        const content = JSON.stringify(results, null, 2);

        return {
            id: `db_${Date.now()}`,
            name: `${table}.json`,
            content,
            format: 'json'
        };
    }
}

// Usage:
// Load input with URI: db://prod/orders?query=SELECT * FROM orders WHERE status='pending'
```

---

## Appendix A: Complete Type Definitions

See `packages/common/src/types.ts` for complete type definitions.

---

## Appendix B: JSON-RPC Full Protocol Specification

See daemon implementation in `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/DaemonCommand.kt`

---

## Appendix C: Monaco Editor Integration

### Registering Language

```typescript
import * as monaco from '@theia/monaco-editor-core';

monaco.languages.register({
    id: 'utlx',
    extensions: ['.utlx'],
    aliases: ['UTL-X', 'utlx'],
    mimetypes: ['text/x-utlx']
});
```

### Syntax Highlighting

```typescript
monaco.languages.setMonarchTokensProvider('utlx', {
    keywords: [
        'input', 'output', 'let', 'if', 'else', 'match', 'case',
        'function', 'def', 'return', 'import', 'as', 'from'
    ],

    operators: [
        '=>', '|>', '==', '!=', '<', '>', '<=', '>=',
        '+', '-', '*', '/', '%', '&&', '||', '!', '?'
    ],

    tokenizer: {
        root: [
            // Keywords
            [/\b(input|output|let|if|else|match|case|function|def)\b/, 'keyword'],

            // Operators
            [/=>|[|]>/, 'operator'],

            // Numbers
            [/\d+(\.\d+)?/, 'number'],

            // Strings
            [/"([^"\\]|\\.)*$/, 'string.invalid'],
            [/"/, { token: 'string.quote', bracket: '@open', next: '@string' }],

            // Comments
            [/\/\/.*$/, 'comment'],

            // Variables
            [/\$[a-zA-Z_]\w*/, 'variable'],

            // Functions
            [/[a-z][a-zA-Z0-9_]*(?=\()/, 'function']
        ],

        string: [
            [/[^\\"]+/, 'string'],
            [/"/, { token: 'string.quote', bracket: '@close', next: '@pop' }]
        ]
    }
});
```

### Diagnostics Integration

```typescript
const diagnosticCollection = monaco.editor.createModel(
    uri,
    'utlx',
    content
);

function showDiagnostics(diagnostics: Diagnostic[]): void {
    const markers: monaco.editor.IMarkerData[] = diagnostics.map(d => ({
        severity: convertSeverity(d.severity),
        startLineNumber: d.location.line,
        startColumn: d.location.column,
        endLineNumber: d.location.line,
        endColumn: d.location.column + d.location.length,
        message: d.message,
        code: d.code,
        source: 'utlx'
    }));

    monaco.editor.setModelMarkers(diagnosticCollection, 'utlx', markers);
}

function convertSeverity(severity: DiagnosticSeverity): monaco.MarkerSeverity {
    switch (severity) {
        case DiagnosticSeverity.ERROR:
            return monaco.MarkerSeverity.Error;
        case DiagnosticSeverity.WARNING:
            return monaco.MarkerSeverity.Warning;
        case DiagnosticSeverity.INFO:
            return monaco.MarkerSeverity.Info;
        case DiagnosticSeverity.HINT:
            return monaco.MarkerSeverity.Hint;
    }
}
```

---

## Appendix D: Performance Guidelines

### Frontend Performance

**Debouncing:**
- Validation: 500ms debounce
- Auto-execution: 500-1000ms debounce
- Input changes: 300ms debounce

**Caching:**
- Cache validation results by document version
- Cache parsed AST in daemon
- Cache function metadata

**Lazy Loading:**
- Load output renderers on demand
- Lazy load syntax highlighting
- Defer non-critical widgets

### Backend Performance

**Daemon Management:**
- Keep daemon alive for session duration
- Implement health checks (ping every 30s)
- Restart daemon on 3 consecutive failures

**Request Optimization:**
- Batch requests when possible
- Use incremental parsing for edits
- Cache intermediate results

**Memory Management:**
- Limit output size (default: 10MB)
- Clear old AST cache entries
- Monitor daemon memory usage

---

## Appendix E: Testing

### Unit Tests

```typescript
import { expect } from 'chai';
import { Container } from '@theia/core/shared/inversify';
import { UTLXService } from '../common/protocol';

describe('UTLXService', () => {
    let container: Container;
    let service: UTLXService;

    beforeEach(() => {
        container = createTestContainer();
        service = container.get(UTLXService);
    });

    it('should parse valid UTL-X', async () => {
        const source = '%utlx 1.0\ninput json\noutput json\n---\n{ result: $input }';
        const result = await service.parse(source);

        expect(result.ast).to.not.be.null;
        expect(result.errors).to.be.empty;
    });

    it('should detect syntax errors', async () => {
        const source = '%utlx 1.0\ninput json\noutput json\n---\n{ invalid syntax }';
        const result = await service.parse(source);

        expect(result.ast).to.be.null;
        expect(result.errors).to.not.be.empty;
        expect(result.errors[0].severity).to.equal('ERROR');
    });
});
```

### Integration Tests

```typescript
describe('Transformation Execution', () => {
    it('should transform XML to JSON', async () => {
        const source = `
%utlx 1.0
input xml
output json
---
{ customer: $input.Customer.Name }
        `.trim();

        const inputs = [{
            id: 'input',
            name: 'test.xml',
            content: '<Customer><Name>John</Name></Customer>',
            format: 'xml' as const
        }];

        const result = await service.execute(source, inputs);

        expect(result.success).to.be.true;
        expect(result.output).to.equal('{"customer":"John"}');
    });
});
```

---

## Appendix F: Migration Guide

### From CLI to Extension

**Before (CLI):**
```bash
utlx transform transform.utlx input.xml -o output.json
```

**After (Extension):**
1. Open `transform.utlx` in editor
2. Load `input.xml` in Input Panel
3. Click "Execute" or enable auto-execution
4. View output in Output Panel

### From DataWeave

**DataWeave:**
```dataweave
%dw 2.0
output application/json
---
{
  orders: payload.Orders.Order map (order) -> {
    id: order.@id,
    total: sum(order.Items.*Item.(@price * @quantity))
  }
}
```

**UTL-X:**
```utlx
%utlx 1.0
input xml
output json
---
{
  orders: $input.Orders.Order |> map(order => {
    id: order.@id,
    total: sum(order.Items.Item.(@price * @quantity))
  })
}
```

**Key Differences:**
- `payload` → `$input`
- `map (x) -> { ... }` → `map(x => { ... })`
- `*Item` → `Item` (wildcard implicit in UTL-X)

---

## Appendix G: Troubleshooting

### Daemon Not Starting

**Symptom:** Extension shows "Daemon not responding"

**Solutions:**
1. Check `utlx` binary is in PATH
2. Set custom path in settings: `utlx.daemonPath`
3. Check daemon logs: `~/.utlx/logs/daemon.log`
4. Restart Theia

### High Memory Usage

**Symptom:** Daemon uses excessive memory

**Solutions:**
1. Clear AST cache: restart daemon
2. Limit output size: `utlx.output.maxSize`
3. Disable auto-execution if not needed
4. Check for transformation infinite loops

### Slow Execution

**Symptom:** Transformations take >1 second

**Solutions:**
1. Profile transformation (enable debug mode)
2. Optimize transformation (avoid nested maps)
3. Check input document sizes
4. Increase daemon heap: `JAVA_OPTS=-Xmx2g utlx daemon`

---

**End of API Reference**

**Version:** 1.0.0
**Last Updated:** 2025-10-29
**Maintainers:** UTL-X Core Team
**License:** AGPL-3.0 / Commercial (see LICENSE.md)
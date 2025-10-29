# UTL-X Theia Extension: Implementation Guide

**Document Version:** 1.0
**Last Updated:** 2025-10-29
**Status:** Implementation Guide
**Author:** UTL-X Implementation Team
**Related Documents:**
- [theia-ide-extension-study.md](../architecture/theia-ide-extension-study.md)
- [nodejs-vs-daemon-analysis.md](../architecture/nodejs-vs-daemon-analysis.md)

---

## Executive Summary

This guide provides step-by-step instructions for implementing the UTL-X Theia extension using the **CLI Daemon Mode** architecture (Phase 1 approach).

**Implementation Timeline:** 8 weeks
**Primary Technologies:** TypeScript, Theia Framework, Kotlin (daemon)
**Architecture:** Theia Backend ↔ CLI Daemon (stdio) ↔ JVM Core

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Project Setup](#2-project-setup)
3. [Week 1-2: CLI Daemon Server](#3-week-1-2-cli-daemon-server)
4. [Week 3-4: Node.js Daemon Client](#4-week-3-4-nodejs-daemon-client)
5. [Week 5-6: Theia Extension](#5-week-5-6-theia-extension)
6. [Week 7-8: Testing & Polish](#6-week-7-8-testing--polish)
7. [Deployment](#7-deployment)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Prerequisites

### 1.1 Development Environment

**Required Software:**
- Node.js 18+ (LTS)
- Java 11+ (for JVM daemon)
- Kotlin 1.9+
- Yarn or npm
- Git

**Development Tools:**
- VS Code or IntelliJ IDEA
- Theia Blueprint (for testing)
- Postman or curl (for testing daemon protocol)

### 1.2 Knowledge Requirements

**Must Have:**
- TypeScript/JavaScript
- Theia framework basics
- Kotlin (for daemon server)
- JSON-RPC protocol
- Process management (Node.js child_process)

**Nice to Have:**
- Inversify (DI framework)
- React (for UI components)
- LSP protocol
- Gradle/Maven (for JVM builds)

### 1.3 Repository Structure

```
utl-x/
├── modules/
│   ├── cli/                    # Existing CLI (add daemon command here)
│   ├── core/                   # Existing core implementation
│   └── ...
├── theia-extension/            # New Theia extension
│   ├── utlx-theia-extension/   # Main extension package
│   │   ├── src/
│   │   │   ├── browser/        # Frontend code
│   │   │   ├── node/           # Backend code
│   │   │   └── common/         # Shared types
│   │   ├── package.json
│   │   └── tsconfig.json
│   └── utlx-theia-app/         # Theia application (for testing)
│       ├── src/
│       ├── package.json
│       └── ...
└── docs/
    └── implementation/
        └── theia-extension-implementation-guide.md  # This file
```

---

## 2. Project Setup

### 2.1 Create Theia Extension Package

```bash
# Navigate to project root
cd utl-x

# Create theia-extension directory
mkdir -p theia-extension
cd theia-extension

# Create extension package
mkdir utlx-theia-extension
cd utlx-theia-extension

# Initialize package.json
npm init -y
```

**package.json:**

```json
{
  "name": "utlx-theia-extension",
  "version": "0.1.0",
  "description": "UTL-X IDE extension for Eclipse Theia",
  "keywords": ["theia-extension"],
  "license": "AGPL-3.0",
  "theiaExtensions": [
    {
      "frontend": "lib/browser/frontend-module",
      "backend": "lib/node/backend-module"
    }
  ],
  "dependencies": {
    "@theia/core": "^1.45.0",
    "@theia/filesystem": "^1.45.0",
    "@theia/workspace": "^1.45.0",
    "@theia/editor": "^1.45.0",
    "@theia/monaco": "^1.45.0",
    "@theia/languages": "^1.45.0",
    "@theia/messages": "^1.45.0",
    "inversify": "^6.0.1",
    "uuid": "^9.0.0"
  },
  "devDependencies": {
    "@types/node": "^18.0.0",
    "@types/uuid": "^9.0.0",
    "typescript": "^5.0.0"
  },
  "scripts": {
    "prepare": "yarn run clean && yarn run build",
    "clean": "rimraf lib",
    "build": "tsc",
    "watch": "tsc -w"
  }
}
```

**tsconfig.json:**

```json
{
  "compilerOptions": {
    "target": "ES2017",
    "module": "commonjs",
    "lib": ["ES2017"],
    "jsx": "react",
    "declaration": true,
    "declarationMap": true,
    "sourceMap": true,
    "outDir": "./lib",
    "rootDir": "./src",
    "composite": true,
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "experimentalDecorators": true,
    "emitDecoratorMetadata": true,
    "resolveJsonModule": true
  },
  "include": ["src"],
  "exclude": ["node_modules"]
}
```

### 2.2 Create Directory Structure

```bash
mkdir -p src/browser
mkdir -p src/node
mkdir -p src/common

# Browser (frontend) subdirectories
mkdir -p src/browser/input-panel
mkdir -p src/browser/output-panel
mkdir -p src/browser/editor
mkdir -p src/browser/workbench

# Node (backend) subdirectories
mkdir -p src/node/daemon
mkdir -p src/node/services
```

### 2.3 Install Dependencies

```bash
yarn install
```

---

## 3. Week 1-2: CLI Daemon Server

### 3.1 Add Daemon Command to CLI

**Location:** `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/DaemonCommand.kt`

```kotlin
package org.apache.utlx.cli.commands

import picocli.CommandLine.*
import java.io.*
import java.util.concurrent.*
import kotlinx.serialization.json.*
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.interpreter.Interpreter
import java.util.concurrent.ConcurrentHashMap

@Command(
    name = "daemon",
    description = ["Run UTL-X as daemon server for IDE integration"]
)
class DaemonCommand : Callable<Int> {

    @Option(
        names = ["--stdio"],
        description = ["Use stdin/stdout for communication (default)"]
    )
    var useStdio: Boolean = true

    @Option(
        names = ["--log-file"],
        description = ["Log file path for debugging"]
    )
    var logFile: String? = null

    override fun call(): Int {
        // Setup logging
        logFile?.let { setupLogging(it) }

        if (useStdio) {
            StdioDaemonServer().start()
        } else {
            error("Socket mode not yet implemented")
        }

        return 0
    }

    private fun setupLogging(path: String) {
        // Redirect stderr to log file
        System.setErr(PrintStream(FileOutputStream(path, true)))
    }
}
```

### 3.2 Implement Stdio Daemon Server

**Location:** `modules/cli/src/main/kotlin/org/apache/utlx/cli/daemon/StdioDaemonServer.kt`

```kotlin
package org.apache.utlx.cli.daemon

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.apache.utlx.core.parser.*
import org.apache.utlx.core.interpreter.*
import org.apache.utlx.core.types.TypeChecker
import java.io.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Daemon server using stdin/stdout for JSON-RPC communication
 */
class StdioDaemonServer {

    private val parser = Parser()
    private val typeChecker = TypeChecker()
    private val interpreter = Interpreter()

    // Cache for parsed ASTs
    private val astCache = ConcurrentHashMap<String, CachedAST>()

    // JSON serializer
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    fun start() {
        val reader = BufferedReader(InputStreamReader(System.`in`))
        val writer = BufferedWriter(OutputStreamWriter(System.out))

        log("UTL-X Daemon started")

        try {
            while (true) {
                // Read JSON-RPC request
                val line = reader.readLine() ?: break

                if (line.isBlank()) continue

                log("Received: $line")

                try {
                    // Parse request
                    val request = json.decodeFromString<JsonRpcRequest>(line)

                    // Handle request
                    val response = handleRequest(request)

                    // Send response
                    val responseJson = json.encodeToString(response)
                    writer.write(responseJson)
                    writer.newLine()
                    writer.flush()

                    log("Sent: $responseJson")
                } catch (e: Exception) {
                    log("Error handling request: ${e.message}")
                    e.printStackTrace()

                    // Send error response
                    val errorResponse = JsonRpcResponse(
                        id = null,
                        result = null,
                        error = JsonRpcError(
                            code = -32603,
                            message = e.message ?: "Internal error"
                        )
                    )
                    val errorJson = json.encodeToString(errorResponse)
                    writer.write(errorJson)
                    writer.newLine()
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            log("Fatal error: ${e.message}")
            e.printStackTrace()
        } finally {
            log("UTL-X Daemon stopped")
        }
    }

    private fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            val result = when (request.method) {
                "ping" -> handlePing(request.params)
                "parse" -> handleParse(request.params)
                "validate" -> handleValidate(request.params)
                "execute" -> handleExecute(request.params)
                "complete" -> handleComplete(request.params)
                "hover" -> handleHover(request.params)
                else -> throw IllegalArgumentException("Unknown method: ${request.method}")
            }

            JsonRpcResponse(
                id = request.id,
                result = result,
                error = null
            )
        } catch (e: Exception) {
            log("Error in ${request.method}: ${e.message}")

            JsonRpcResponse(
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32603,
                    message = e.message ?: "Internal error"
                )
            )
        }
    }

    private fun handlePing(params: JsonElement?): JsonElement {
        return JsonPrimitive("pong")
    }

    private fun handleParse(params: JsonElement?): JsonElement {
        val obj = params?.jsonObject ?: throw IllegalArgumentException("Missing params")
        val source = obj["source"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing source")
        val documentId = obj["documentId"]?.jsonPrimitive?.content

        log("Parsing document: $documentId")

        // Check cache
        if (documentId != null && astCache.containsKey(documentId)) {
            log("Returning cached AST for: $documentId")
            return buildJsonObject {
                put("cached", true)
                put("ast", JsonPrimitive("(cached)"))
            }
        }

        // Parse
        val parseResult = parser.parse(source)

        return when (parseResult) {
            is ParseResult.Success -> {
                // Cache AST
                if (documentId != null) {
                    astCache[documentId] = CachedAST(
                        parseResult.program,
                        System.currentTimeMillis()
                    )
                }

                buildJsonObject {
                    put("success", true)
                    put("ast", JsonPrimitive("(AST structure)")) // Simplified
                }
            }
            is ParseResult.Failure -> {
                buildJsonObject {
                    put("success", false)
                    put("errors", JsonArray(
                        parseResult.errors.map { error ->
                            buildJsonObject {
                                put("line", error.location.line)
                                put("column", error.location.column)
                                put("message", error.message)
                            }
                        }
                    ))
                }
            }
        }
    }

    private fun handleValidate(params: JsonElement?): JsonElement {
        val obj = params?.jsonObject ?: throw IllegalArgumentException("Missing params")
        val source = obj["source"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing source")

        log("Validating source")

        // Parse
        val parseResult = parser.parse(source)
        if (parseResult is ParseResult.Failure) {
            return buildJsonObject {
                put("valid", false)
                put("diagnostics", JsonArray(
                    parseResult.errors.map { error ->
                        buildJsonObject {
                            put("severity", "error")
                            put("line", error.location.line)
                            put("column", error.location.column)
                            put("message", error.message)
                        }
                    }
                ))
            }
        }

        val program = (parseResult as ParseResult.Success).program

        // Type check
        val typeCheckResult = typeChecker.check(program)

        val diagnostics = typeCheckResult.errors.map { error ->
            buildJsonObject {
                put("severity", "error")
                put("line", error.location.line)
                put("column", error.location.column)
                put("message", error.message)
            }
        }

        return buildJsonObject {
            put("valid", diagnostics.isEmpty())
            put("diagnostics", JsonArray(diagnostics))
        }
    }

    private fun handleExecute(params: JsonElement?): JsonElement {
        val obj = params?.jsonObject ?: throw IllegalArgumentException("Missing params")
        val source = obj["source"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing source")
        val inputs = obj["inputs"]?.jsonArray
            ?: throw IllegalArgumentException("Missing inputs")

        log("Executing transformation with ${inputs.size} input(s)")

        // TODO: Implement execution
        // For now, return mock result

        return buildJsonObject {
            put("success", true)
            put("output", JsonPrimitive("{\"result\": \"mock output\"}"))
            put("outputFormat", JsonPrimitive("json"))
            put("executionTime", JsonPrimitive(42))
        }
    }

    private fun handleComplete(params: JsonElement?): JsonElement {
        // TODO: Implement completion
        return JsonArray(emptyList())
    }

    private fun handleHover(params: JsonElement?): JsonElement {
        // TODO: Implement hover
        return JsonNull
    }

    private fun log(message: String) {
        System.err.println("[${java.time.Instant.now()}] $message")
    }
}

data class CachedAST(
    val program: Program,
    val timestamp: Long
)

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)
```

### 3.3 Add Daemon Command to Main

**Location:** `modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt`

```kotlin
@Command(
    name = "utlx",
    subcommands = [
        TransformCommand::class,
        ValidateCommand::class,
        LintCommand::class,
        ReplCommand::class,
        DaemonCommand::class,  // Add this
        // ... other commands
    ]
)
class Main
```

### 3.4 Build and Test Daemon

```bash
# Build CLI
cd modules/cli
./gradlew jar

# Test daemon manually
echo '{"method":"ping","id":1}' | java -jar build/libs/cli-1.0.0-SNAPSHOT.jar daemon --stdio

# Expected output:
# {"jsonrpc":"2.0","id":1,"result":"pong","error":null}
```

---

## 4. Week 3-4: Node.js Daemon Client

### 4.1 Define Common Protocol

**Location:** `theia-extension/utlx-theia-extension/src/common/protocol.ts`

```typescript
export const UTLX_SERVICE_PATH = '/services/utlx';
export const UTLXService = Symbol('UTLXService');

/**
 * UTL-X service interface
 */
export interface UTLXService {
    /**
     * Parse UTL-X source
     */
    parse(source: string, documentId?: string): Promise<ParseResult>;

    /**
     * Validate transformation
     */
    validate(source: string): Promise<ValidationResult>;

    /**
     * Execute transformation
     */
    execute(source: string, inputs: InputDocument[]): Promise<ExecutionResult>;

    /**
     * Get completions
     */
    complete(source: string, line: number, column: number): Promise<CompletionItem[]>;

    /**
     * Get hover information
     */
    hover(source: string, line: number, column: number): Promise<HoverInfo | null>;
}

export interface ParseResult {
    success: boolean;
    ast?: any;
    errors?: ParseError[];
    cached?: boolean;
}

export interface ParseError {
    line: number;
    column: number;
    message: string;
}

export interface ValidationResult {
    valid: boolean;
    diagnostics: Diagnostic[];
}

export interface Diagnostic {
    severity: 'error' | 'warning' | 'info' | 'hint';
    line: number;
    column: number;
    message: string;
    code?: string;
}

export interface InputDocument {
    id: string;
    name: string;
    content: string;
    format: 'xml' | 'json' | 'csv' | 'yaml';
}

export interface ExecutionResult {
    success: boolean;
    output?: string;
    outputFormat?: string;
    errors?: ExecutionError[];
    executionTime?: number;
}

export interface ExecutionError {
    line: number;
    column: number;
    message: string;
}

export interface CompletionItem {
    label: string;
    kind: string;
    detail?: string;
    documentation?: string;
}

export interface HoverInfo {
    content: string;
    range?: {
        startLine: number;
        startColumn: number;
        endLine: number;
        endColumn: number;
    };
}
```

### 4.2 Implement Daemon Client

**Location:** `theia-extension/utlx-theia-extension/src/node/daemon/daemon-client.ts`

```typescript
import { spawn, ChildProcess } from 'child_process';
import { EventEmitter } from 'events';
import { injectable } from 'inversify';

interface JsonRpcRequest {
    jsonrpc: string;
    id: number;
    method: string;
    params?: any;
}

interface JsonRpcResponse {
    jsonrpc: string;
    id: number;
    result?: any;
    error?: {
        code: number;
        message: string;
    };
}

interface PendingRequest {
    resolve: (value: any) => void;
    reject: (error: Error) => void;
    timeout: NodeJS.Timeout;
}

@injectable()
export class UTLXDaemonClient extends EventEmitter {

    private process: ChildProcess | null = null;
    private requestId = 0;
    private pendingRequests = new Map<number, PendingRequest>();
    private buffer = '';

    /**
     * Start daemon process
     */
    async start(): Promise<void> {
        console.log('[UTLXDaemonClient] Starting daemon...');

        // Find utlx CLI
        const utlxPath = this.findUTLXPath();

        // Spawn daemon
        this.process = spawn(utlxPath, ['daemon', '--stdio'], {
            stdio: ['pipe', 'pipe', 'pipe']
        });

        // Setup stdout handling
        this.process.stdout!.on('data', (data) => {
            this.handleStdout(data);
        });

        // Setup stderr logging
        this.process.stderr!.on('data', (data) => {
            console.log('[UTLXDaemon stderr]', data.toString());
        });

        // Handle process exit
        this.process.on('exit', (code) => {
            console.error('[UTLXDaemonClient] Daemon exited with code:', code);
            this.emit('exit', code);

            // Reject all pending requests
            for (const [id, pending] of this.pendingRequests) {
                clearTimeout(pending.timeout);
                pending.reject(new Error('Daemon process exited'));
            }
            this.pendingRequests.clear();
        });

        // Wait for daemon to be ready
        await this.ping();

        console.log('[UTLXDaemonClient] Daemon ready');
    }

    /**
     * Stop daemon
     */
    stop(): void {
        if (this.process) {
            console.log('[UTLXDaemonClient] Stopping daemon...');
            this.process.kill();
            this.process = null;
        }
    }

    /**
     * Send request to daemon
     */
    async request(method: string, params?: any): Promise<any> {
        return new Promise((resolve, reject) => {
            if (!this.process) {
                reject(new Error('Daemon not started'));
                return;
            }

            const id = ++this.requestId;

            const request: JsonRpcRequest = {
                jsonrpc: '2.0',
                id,
                method,
                params
            };

            // Setup timeout
            const timeout = setTimeout(() => {
                this.pendingRequests.delete(id);
                reject(new Error(`Request timeout: ${method}`));
            }, 30000); // 30 second timeout

            // Store pending request
            this.pendingRequests.set(id, { resolve, reject, timeout });

            // Send request
            const message = JSON.stringify(request) + '\n';
            this.process.stdin!.write(message);
        });
    }

    /**
     * Ping daemon
     */
    async ping(): Promise<void> {
        const result = await this.request('ping');
        if (result !== 'pong') {
            throw new Error('Invalid ping response');
        }
    }

    /**
     * Parse source
     */
    async parse(source: string, documentId?: string): Promise<any> {
        return this.request('parse', { source, documentId });
    }

    /**
     * Validate source
     */
    async validate(source: string): Promise<any> {
        return this.request('validate', { source });
    }

    /**
     * Execute transformation
     */
    async execute(source: string, inputs: any[]): Promise<any> {
        return this.request('execute', { source, inputs });
    }

    /**
     * Handle stdout data
     */
    private handleStdout(data: Buffer): void {
        this.buffer += data.toString();

        // Process complete messages (separated by newlines)
        let newlineIndex: number;
        while ((newlineIndex = this.buffer.indexOf('\n')) !== -1) {
            const line = this.buffer.substring(0, newlineIndex);
            this.buffer = this.buffer.substring(newlineIndex + 1);

            if (line.trim()) {
                try {
                    const response: JsonRpcResponse = JSON.parse(line);
                    this.handleResponse(response);
                } catch (error) {
                    console.error('[UTLXDaemonClient] Failed to parse response:', line, error);
                }
            }
        }
    }

    /**
     * Handle JSON-RPC response
     */
    private handleResponse(response: JsonRpcResponse): void {
        const pending = this.pendingRequests.get(response.id);

        if (!pending) {
            console.warn('[UTLXDaemonClient] Received response for unknown request:', response.id);
            return;
        }

        // Clear timeout
        clearTimeout(pending.timeout);

        // Remove from pending
        this.pendingRequests.delete(response.id);

        // Resolve or reject
        if (response.error) {
            pending.reject(new Error(response.error.message));
        } else {
            pending.resolve(response.result);
        }
    }

    /**
     * Find utlx CLI path
     */
    private findUTLXPath(): string {
        // TODO: Make this configurable
        // For now, assume utlx is in PATH
        return 'utlx';
    }
}
```

### 4.3 Implement Backend Service

**Location:** `theia-extension/utlx-theia-extension/src/node/services/utlx-service-impl.ts`

```typescript
import { injectable, inject, postConstruct } from 'inversify';
import {
    UTLXService,
    ParseResult,
    ValidationResult,
    ExecutionResult,
    InputDocument,
    CompletionItem,
    HoverInfo
} from '../../common/protocol';
import { UTLXDaemonClient } from '../daemon/daemon-client';

@injectable()
export class UTLXServiceImpl implements UTLXService {

    @inject(UTLXDaemonClient)
    protected readonly daemon!: UTLXDaemonClient;

    @postConstruct()
    protected async init(): Promise<void> {
        // Start daemon on initialization
        try {
            await this.daemon.start();
        } catch (error) {
            console.error('Failed to start UTL-X daemon:', error);
            throw error;
        }
    }

    async parse(source: string, documentId?: string): Promise<ParseResult> {
        try {
            const result = await this.daemon.parse(source, documentId);
            return result;
        } catch (error) {
            console.error('Parse error:', error);
            throw error;
        }
    }

    async validate(source: string): Promise<ValidationResult> {
        try {
            const result = await this.daemon.validate(source);
            return result;
        } catch (error) {
            console.error('Validation error:', error);
            throw error;
        }
    }

    async execute(source: string, inputs: InputDocument[]): Promise<ExecutionResult> {
        try {
            const result = await this.daemon.execute(source, inputs);
            return result;
        } catch (error) {
            console.error('Execution error:', error);
            throw error;
        }
    }

    async complete(source: string, line: number, column: number): Promise<CompletionItem[]> {
        // TODO: Implement
        return [];
    }

    async hover(source: string, line: number, column: number): Promise<HoverInfo | null> {
        // TODO: Implement
        return null;
    }

    /**
     * Cleanup on shutdown
     */
    dispose(): void {
        this.daemon.stop();
    }
}
```

### 4.4 Register Backend Module

**Location:** `theia-extension/utlx-theia-extension/src/node/backend-module.ts`

```typescript
import { ContainerModule } from 'inversify';
import { ConnectionHandler, JsonRpcConnectionHandler } from '@theia/core';
import { UTLXService, UTLX_SERVICE_PATH } from '../common/protocol';
import { UTLXServiceImpl } from './services/utlx-service-impl';
import { UTLXDaemonClient } from './daemon/daemon-client';

export default new ContainerModule(bind => {
    // Bind daemon client
    bind(UTLXDaemonClient).toSelf().inSingletonScope();

    // Bind service implementation
    bind(UTLXService).to(UTLXServiceImpl).inSingletonScope();

    // Bind connection handler for frontend-backend RPC
    bind(ConnectionHandler).toDynamicValue(ctx =>
        new JsonRpcConnectionHandler(UTLX_SERVICE_PATH, () => {
            return ctx.container.get<UTLXService>(UTLXService);
        })
    ).inSingletonScope();
});
```

### 4.5 Test Daemon Client

Create test file:

**Location:** `theia-extension/utlx-theia-extension/src/node/__tests__/daemon-client.test.ts`

```typescript
import { UTLXDaemonClient } from '../daemon/daemon-client';

describe('UTLXDaemonClient', () => {

    let client: UTLXDaemonClient;

    beforeAll(async () => {
        client = new UTLXDaemonClient();
        await client.start();
    });

    afterAll(() => {
        client.stop();
    });

    test('ping should return pong', async () => {
        await expect(client.ping()).resolves.toBeUndefined();
    });

    test('parse valid source', async () => {
        const source = `
%utlx 1.0
input xml
output json
---
{ result: "test" }
        `;

        const result = await client.parse(source);
        expect(result.success).toBe(true);
    });

    test('validate valid source', async () => {
        const source = `
%utlx 1.0
input xml
output json
---
{ result: "test" }
        `;

        const result = await client.validate(source);
        expect(result.valid).toBe(true);
    });
});
```

Run tests:

```bash
yarn test
```

---

## 5. Week 5-6: Theia Extension

### 5.1 Implement Input Panel Widget

**Location:** `theia-extension/utlx-theia-extension/src/browser/input-panel/input-panel-widget.tsx`

```typescript
import * as React from 'react';
import { injectable, inject, postConstruct } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { Message } from '@theia/core/lib/browser';

@injectable()
export class InputPanelWidget extends ReactWidget {

    static readonly ID = 'utlx-input-panel';
    static readonly LABEL = 'Inputs';

    protected inputs: InputDocument[] = [];

    constructor() {
        super();
        this.id = InputPanelWidget.ID;
        this.title.label = InputPanelWidget.LABEL;
        this.title.caption = InputPanelWidget.LABEL;
        this.title.closable = true;
        this.title.iconClass = 'fa fa-file-import';
    }

    @postConstruct()
    protected init(): void {
        this.update();
    }

    protected render(): React.ReactNode {
        return (
            <div className='utlx-input-panel'>
                <div className='utlx-input-panel-toolbar'>
                    <button
                        className='theia-button primary'
                        onClick={() => this.handleAddInput()}
                    >
                        <i className='fa fa-plus' /> Add Input
                    </button>
                </div>

                <div className='utlx-input-list'>
                    {this.inputs.length === 0 ? (
                        <div className='utlx-input-empty'>
                            <p>No input documents</p>
                        </div>
                    ) : (
                        this.inputs.map(input => (
                            <div key={input.id} className='utlx-input-item'>
                                <span>{input.name}</span>
                                <span className='utlx-input-format'>{input.format}</span>
                            </div>
                        ))
                    )}
                </div>
            </div>
        );
    }

    private handleAddInput(): void {
        // TODO: Open file dialog
        console.log('Add input clicked');
    }

    protected onUpdateRequest(msg: Message): void {
        super.onUpdateRequest(msg);
    }
}

interface InputDocument {
    id: string;
    name: string;
    format: string;
    content: string;
}
```

### 5.2 Implement Output Panel Widget

**Location:** `theia-extension/utlx-theia-extension/src/browser/output-panel/output-panel-widget.tsx`

```typescript
import * as React from 'react';
import { injectable, postConstruct } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';

@injectable()
export class OutputPanelWidget extends ReactWidget {

    static readonly ID = 'utlx-output-panel';
    static readonly LABEL = 'Output';

    protected output: string | null = null;
    protected success: boolean = true;

    constructor() {
        super();
        this.id = OutputPanelWidget.ID;
        this.title.label = OutputPanelWidget.LABEL;
        this.title.caption = OutputPanelWidget.LABEL;
        this.title.closable = true;
        this.title.iconClass = 'fa fa-file-export';
    }

    @postConstruct()
    protected init(): void {
        this.update();
    }

    protected render(): React.ReactNode {
        return (
            <div className='utlx-output-panel'>
                <div className='utlx-output-toolbar'>
                    <button
                        className='theia-button primary'
                        onClick={() => this.handleExecute()}
                    >
                        <i className='fa fa-play' /> Execute
                    </button>
                </div>

                <div className='utlx-output-content'>
                    {this.output ? (
                        <pre>{this.output}</pre>
                    ) : (
                        <div className='utlx-output-empty'>
                            <p>No output yet</p>
                            <p className='hint'>Execute transformation to see output</p>
                        </div>
                    )}
                </div>
            </div>
        );
    }

    private handleExecute(): void {
        // TODO: Execute transformation
        console.log('Execute clicked');
    }

    /**
     * Update output
     */
    setOutput(output: string, success: boolean): void {
        this.output = output;
        this.success = success;
        this.update();
    }
}
```

### 5.3 Register Frontend Module

**Location:** `theia-extension/utlx-theia-extension/src/browser/frontend-module.ts`

```typescript
import { ContainerModule } from 'inversify';
import { WidgetFactory } from '@theia/core/lib/browser';
import { WebSocketConnectionProvider } from '@theia/core/lib/browser';
import { InputPanelWidget } from './input-panel/input-panel-widget';
import { OutputPanelWidget } from './output-panel/output-panel-widget';
import { UTLXService, UTLX_SERVICE_PATH } from '../common/protocol';

export default new ContainerModule(bind => {
    // Bind service proxy (connects to backend)
    bind(UTLXService).toDynamicValue(ctx => {
        const connection = ctx.container.get(WebSocketConnectionProvider);
        return connection.createProxy<UTLXService>(UTLX_SERVICE_PATH);
    }).inSingletonScope();

    // Input panel
    bind(InputPanelWidget).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: InputPanelWidget.ID,
        createWidget: () => ctx.container.get<InputPanelWidget>(InputPanelWidget)
    })).inSingletonScope();

    // Output panel
    bind(OutputPanelWidget).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: OutputPanelWidget.ID,
        createWidget: () => ctx.container.get<OutputPanelWidget>(OutputPanelWidget)
    })).inSingletonScope();
});
```

### 5.4 Create Theia Application for Testing

**Location:** `theia-extension/utlx-theia-app/package.json`

```json
{
  "name": "utlx-theia-app",
  "version": "0.1.0",
  "private": true,
  "dependencies": {
    "@theia/core": "^1.45.0",
    "@theia/filesystem": "^1.45.0",
    "@theia/workspace": "^1.45.0",
    "@theia/editor": "^1.45.0",
    "@theia/monaco": "^1.45.0",
    "@theia/messages": "^1.45.0",
    "@theia/navigator": "^1.45.0",
    "@theia/terminal": "^1.45.0",
    "utlx-theia-extension": "^0.1.0"
  },
  "devDependencies": {
    "@theia/cli": "^1.45.0"
  },
  "scripts": {
    "prepare": "yarn run clean && yarn build",
    "clean": "theia clean",
    "build": "theia build --mode development",
    "start": "theia start --plugins=local-dir:plugins",
    "watch": "theia build --watch --mode development"
  },
  "theiaPluginsDir": "plugins",
  "theiaPlugins": {}
}
```

### 5.5 Build and Run

```bash
# Build extension
cd theia-extension/utlx-theia-extension
yarn build

# Build app
cd ../utlx-theia-app
yarn install
yarn build

# Start Theia
yarn start

# Open browser: http://localhost:3000
```

---

## 6. Week 7-8: Testing & Polish

### 6.1 Integration Testing

Create integration test suite:

```typescript
// Test daemon + service integration
describe('UTL-X Service Integration', () => {

    test('validate transformation', async () => {
        const service = container.get<UTLXService>(UTLXService);

        const source = `
%utlx 1.0
input xml
output json
---
{ result: $input.data }
        `;

        const result = await service.validate(source);
        expect(result.valid).toBe(true);
    });

    test('execute transformation', async () => {
        const service = container.get<UTLXService>(UTLXService);

        const source = `
%utlx 1.0
input json
output json
---
{ result: "test" }
        `;

        const inputs = [{
            id: '1',
            name: 'input1',
            content: '{"data": "value"}',
            format: 'json' as const
        }];

        const result = await service.execute(source, inputs);
        expect(result.success).toBe(true);
    });
});
```

### 6.2 Performance Testing

```typescript
// Measure daemon response times
describe('Performance Tests', () => {

    test('validation should complete in <100ms', async () => {
        const service = container.get<UTLXService>(UTLXService);
        const source = generateTestSource();

        const start = Date.now();
        await service.validate(source);
        const duration = Date.now() - start;

        expect(duration).toBeLessThan(100);
    });

    test('should handle 100 concurrent validations', async () => {
        const service = container.get<UTLXService>(UTLXService);
        const source = generateTestSource();

        const promises = Array.from({ length: 100 }, () =>
            service.validate(source)
        );

        await expect(Promise.all(promises)).resolves.toBeDefined();
    });
});
```

### 6.3 Error Handling

Add comprehensive error handling:

```typescript
// daemon-client.ts
private handleStdout(data: Buffer): void {
    try {
        this.buffer += data.toString();
        // ... process buffer
    } catch (error) {
        console.error('[UTLXDaemonClient] Error processing stdout:', error);
        this.emit('error', error);
    }
}

// Handle daemon crashes
this.process.on('error', (error) => {
    console.error('[UTLXDaemonClient] Process error:', error);
    this.emit('error', error);
    this.handleDaemonCrash();
});

private handleDaemonCrash(): void {
    console.log('[UTLXDaemonClient] Attempting to restart daemon...');

    // Clear pending requests
    for (const [id, pending] of this.pendingRequests) {
        clearTimeout(pending.timeout);
        pending.reject(new Error('Daemon crashed'));
    }
    this.pendingRequests.clear();

    // Restart after delay
    setTimeout(async () => {
        try {
            await this.start();
            console.log('[UTLXDaemonClient] Daemon restarted successfully');
        } catch (error) {
            console.error('[UTLXDaemonClient] Failed to restart daemon:', error);
        }
    }, 5000);
}
```

### 6.4 User Documentation

Create user guide:

**Location:** `theia-extension/README.md`

```markdown
# UTL-X Theia Extension

IDE extension for developing UTL-X transformations.

## Features

- **Three-panel layout**: Inputs, Transform, Output
- **Real-time validation**: Errors shown as you type
- **Multi-input support**: Test with multiple data sources
- **Live execution**: See results immediately

## Installation

### Prerequisites

- Java 11+ (for UTL-X runtime)
- Node.js 18+

### Install Extension

```bash
# From marketplace
theia extension:install utlx-theia-extension

# Or from source
git clone https://github.com/grauwen/utl-x.git
cd utl-x/theia-extension/utlx-theia-extension
yarn install
yarn build
```

## Usage

### Open Transformation File

1. Create or open `.utlx` file
2. Input panel appears on left
3. Output panel appears on right

### Add Input Documents

1. Click "Add Input" in input panel
2. Select file or paste content
3. Choose format (XML, JSON, CSV, YAML)

### Execute Transformation

1. Write transformation code
2. Press Ctrl+Enter or click "Execute"
3. Output appears in right panel

## Configuration

Configure daemon path in settings:

```json
{
  "utlx.daemon.path": "/path/to/utlx"
}
```

## Troubleshooting

### Daemon Won't Start

- Check Java is installed: `java -version`
- Check utlx is in PATH: `which utlx`
- Check daemon manually: `utlx daemon --stdio`

### Slow Performance

- Increase daemon timeout in settings
- Check available memory
- Restart daemon: Cmd/Ctrl+Shift+P → "UTL-X: Restart Daemon"

## Support

- GitHub Issues: https://github.com/grauwen/utl-x/issues
- Documentation: https://utl-x.dev/docs
```

---

## 7. Deployment

### 7.1 Package Extension

```bash
# Build extension
cd theia-extension/utlx-theia-extension
yarn build

# Create package
npm pack

# Output: utlx-theia-extension-0.1.0.tgz
```

### 7.2 Publish to npm

```bash
# Login to npm
npm login

# Publish
npm publish utlx-theia-extension-0.1.0.tgz

# Tag as latest
npm dist-tag add utlx-theia-extension@0.1.0 latest
```

### 7.3 Create Desktop App

```bash
# Build Electron app
cd theia-extension/utlx-theia-app
yarn electron package

# Outputs platform-specific installers:
# - dist/utlx-ide-0.1.0.dmg (macOS)
# - dist/utlx-ide-0.1.0.exe (Windows)
# - dist/utlx-ide-0.1.0.AppImage (Linux)
```

---

## 8. Troubleshooting

### 8.1 Common Issues

**Issue:** Daemon process won't start

**Solution:**
```bash
# Check Java installation
java -version

# Test daemon manually
cd modules/cli
./gradlew jar
echo '{"method":"ping","id":1}' | java -jar build/libs/cli-1.0.0-SNAPSHOT.jar daemon --stdio

# Check daemon logs
tail -f /tmp/utlx-daemon.log
```

**Issue:** IPC timeout errors

**Solution:**
- Increase timeout in daemon-client.ts (currently 30s)
- Check daemon stderr for errors
- Verify JSON-RPC format

**Issue:** Extension not loading

**Solution:**
```bash
# Rebuild extension
yarn clean && yarn build

# Clear Theia cache
rm -rf .theia

# Restart Theia
```

### 8.2 Debugging

**Backend debugging:**

```json
// launch.json
{
  "type": "node",
  "request": "attach",
  "name": "Attach to Backend",
  "port": 9229
}
```

Start with debugging:
```bash
yarn start --inspect-brk=9229
```

**Frontend debugging:**

Open Chrome DevTools: F12

---

## Appendix A: File Checklist

### Kotlin Files (Daemon)

- [ ] `DaemonCommand.kt` - CLI command
- [ ] `StdioDaemonServer.kt` - Daemon server
- [ ] `JsonRpcProtocol.kt` - Protocol types

### TypeScript Files (Extension)

- [ ] `protocol.ts` - Common types
- [ ] `daemon-client.ts` - Daemon client
- [ ] `utlx-service-impl.ts` - Backend service
- [ ] `backend-module.ts` - Backend DI
- [ ] `input-panel-widget.tsx` - Input panel
- [ ] `output-panel-widget.tsx` - Output panel
- [ ] `frontend-module.ts` - Frontend DI

### Configuration

- [ ] `package.json` - Extension package
- [ ] `tsconfig.json` - TypeScript config
- [ ] `README.md` - Documentation

---

**Implementation Status:** Ready to Start
**Estimated Effort:** 8 weeks
**Team Size:** 2-3 developers
**Next Step:** Begin Week 1 (CLI Daemon Server)

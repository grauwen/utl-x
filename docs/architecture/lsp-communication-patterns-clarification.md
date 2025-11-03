# LSP Communication Patterns - Theia, MCP, and Daemon Clarification

**Version:** 1.0
**Date:** 2025-11-03
**Status:** Architecture Clarification

---

## Question

> "The LSP was built that it could use a websocket but with the Theia design it was direct I/O. Now the MCP and the Theia Node.JS part need to communicate with the LSP (right?), That means the LSP has two connected sessions? (or maybe I misunderstood your MCP architecture)? Please clarify and assess if changes to the current LSP setup in UTLX are necessary."

---

## Short Answer

**CLARIFICATION:** MCP does **NOT** communicate with the LSP/Daemon at all. This was a misunderstanding from the architecture diagrams.

**Current Reality:**
- **Theia Monaco Editor** → UTL-X Daemon (LSP) - **ONE session** (STDIO or Socket)
- **MCP Server** → UTL-X Daemon (HTTP/REST API) - **Separate API** (NOT LSP protocol)

**Required Changes to Current LSP Setup:**
- ✅ **NO changes needed to LSP protocol** (already supports both STDIO and Socket)
- ⚠️ **ADD HTTP/REST API** to daemon for MCP server access
- ⚠️ **Expose validation, type inference, execution APIs** via HTTP

---

## Detailed Architecture Clarification

### What You Currently Have (UTL-X Daemon)

**From `DaemonServer.kt:32-64`:**

```kotlin
class UTLXDaemon(
    private val transportType: TransportType = TransportType.STDIO,
    private val port: Int = 7777
) {
    // ...

    fun start() {
        transport = when (transportType) {
            TransportType.STDIO -> {
                logger.info("Using STDIO transport (standard streams)")
                StdioTransport()
            }
            TransportType.SOCKET -> {
                logger.info("Using Socket transport (port: $port)")
                SocketTransport(port)
            }
        }

        // LSP JSON-RPC over chosen transport
        transport!!.start { request -> handleRequest(request) }
    }
}
```

**What this means:**
- ✅ Daemon **already supports** both STDIO and Socket transports
- ✅ Both use **LSP/JSON-RPC 2.0** protocol
- ✅ STDIO for direct parent-child process communication (Theia backend ↔ Daemon)
- ✅ Socket for network communication (could be used for WebSocket)

---

### Correct Architecture - Three Independent Communication Paths

```
┌─────────────────────────────────────────────────────────────────┐
│                    Theia IDE (Browser)                          │
│                                                                 │
│  ┌──────────────────┐              ┌──────────────────┐        │
│  │  Monaco Editor   │              │  AI Assistant    │        │
│  │  (UTLX code)     │              │  Panel           │        │
│  └────────┬─────────┘              └────────┬─────────┘        │
│           │                                  │                  │
└───────────┼──────────────────────────────────┼──────────────────┘
            │                                  │
            │ ① LSP                            │ ② MCP
            │ (WebSocket or STDIO)             │ (HTTP/JSON-RPC)
            │                                  │
     ┌──────▼────────┐                  ┌──────▼────────┐
     │ Theia Backend │                  │  MCP Server   │
     │ (Node.js)     │                  │  (Node.js)    │
     └──────┬────────┘                  └──────┬────────┘
            │                                  │
            │ ③ LSP JSON-RPC                   │ ④ HTTP/REST API
            │ (STDIO or Socket)                │ (axios/fetch)
            │                                  │
     ┌──────▼──────────────────────────────────▼────────┐
     │          UTL-X Daemon (Kotlin JVM)               │
     │                                                   │
     │  ┌───────────────────┐    ┌──────────────────┐  │
     │  │  LSP Server       │    │  REST API        │  │
     │  │  (JSON-RPC 2.0)   │    │  (HTTP/JSON)     │  │
     │  ├───────────────────┤    ├──────────────────┤  │
     │  │ • Completion      │    │ • /api/validate  │  │
     │  │ • Hover           │    │ • /api/execute   │  │
     │  │ • Diagnostics     │    │ • /api/infer     │  │
     │  │ • Mode switching  │    │ • /api/parse     │  │
     │  └───────────────────┘    └──────────────────┘  │
     │                                                   │
     │  ┌──────────────────────────────────────────┐   │
     │  │  Shared Core Services                    │   │
     │  │  • Parser                                │   │
     │  │  • Type Checker                          │   │
     │  │  • Executor                              │   │
     │  │  • Schema Parser                         │   │
     │  │  • State Manager                         │   │
     │  └──────────────────────────────────────────┘   │
     └───────────────────────────────────────────────────┘
```

---

### Path Analysis

#### Path ① - Monaco Editor → Theia Backend (WebSocket)

**Protocol:** LSP over WebSocket
**Purpose:** Real-time code intelligence in browser

```typescript
// Browser (Monaco Editor)
const websocket = new WebSocket('ws://localhost:8080/lsp');
const languageClient = new MonacoLanguageClient({
  name: 'UTL-X Language Client',
  connectionProvider: {
    get: () => Promise.resolve({
      reader: new WebSocketMessageReader(socket),
      writer: new WebSocketMessageWriter(socket)
    })
  }
});
```

**Why WebSocket?** Browser cannot use STDIO or raw TCP sockets.

---

#### Path ② - AI Assistant Panel → MCP Server (HTTP)

**Protocol:** MCP JSON-RPC over HTTP
**Purpose:** AI generation requests

```typescript
// Browser (AI Assistant Panel)
const response = await fetch('http://localhost:7779/mcp', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    jsonrpc: '2.0',
    id: 1,
    method: 'generate_transformation',
    params: {
      prompt: 'Convert XML orders to JSON invoices',
      inputSchema: '...'
    }
  })
});
```

**Why HTTP?** MCP server is separate service, browser can only use HTTP/WebSocket.

---

#### Path ③ - Theia Backend → Daemon (STDIO or Socket)

**Protocol:** LSP JSON-RPC over STDIO
**Purpose:** Direct parent-child process communication

```typescript
// Theia Backend (Node.js)
import { spawn } from 'child_process';
import { LanguageClient, StreamMessageReader, StreamMessageWriter } from 'vscode-languageserver-protocol';

// Option A: STDIO Transport (parent-child process)
const daemonProcess = spawn('java', [
  '-jar',
  'utlx-daemon.jar',
  '--transport', 'stdio'  // ← STDIO mode
]);

const languageClient = new LanguageClient(
  'utlx',
  'UTL-X Language Server',
  {
    serverOptions: {
      run: { module: daemonProcess.stdout },
      debug: { module: daemonProcess.stdout }
    },
    clientOptions: {
      documentSelector: [{ language: 'utlx' }]
    }
  }
);

// Communication:
// Theia → daemon.stdin (LSP requests)
// daemon.stdout → Theia (LSP responses)
// daemon.stderr → Logs
```

```typescript
// Option B: Socket Transport (network connection)
const daemonProcess = spawn('java', [
  '-jar',
  'utlx-daemon.jar',
  '--transport', 'socket',
  '--port', '7777'  // ← Socket mode
]);

// Wait for daemon to start, then connect
const socket = new net.Socket();
socket.connect(7777, 'localhost');

const languageClient = new LanguageClient(
  'utlx',
  'UTL-X Language Server',
  {
    serverOptions: {
      run: {
        reader: new SocketMessageReader(socket),
        writer: new SocketMessageWriter(socket)
      }
    },
    clientOptions: {
      documentSelector: [{ language: 'utlx' }]
    }
  }
);
```

**Why STDIO vs Socket?**
- **STDIO:** Simpler, direct parent-child, automatic cleanup
- **Socket:** More flexible, can restart daemon independently, can connect from multiple clients

**Recommendation:** Use **STDIO** for Theia ↔ Daemon (simpler, already works).

---

#### Path ④ - MCP Server → Daemon (HTTP/REST API)

**Protocol:** HTTP/REST API (NOT LSP!)
**Purpose:** Schema analysis, validation, execution

**❌ WRONG (what the diagrams implied):**
```typescript
// MCP Server trying to use LSP protocol
const lspClient = new LanguageClient(...);
await lspClient.sendRequest('textDocument/completion', ...);
```

**✅ CORRECT (what should actually happen):**
```typescript
// MCP Server using HTTP/REST API
import axios from 'axios';

class DaemonClient {
  private baseUrl = 'http://localhost:7778';

  async validateUTLX(transformation: string): Promise<ValidationResult> {
    const response = await axios.post(`${this.baseUrl}/api/validate`, {
      transformation
    });
    return response.data;
  }

  async inferOutputSchema(params: InferenceParams): Promise<SchemaResult> {
    const response = await axios.post(`${this.baseUrl}/api/infer-schema`, {
      transformation: params.transformation,
      inputSchema: params.inputSchema,
      inputFormat: params.inputFormat
    });
    return response.data;
  }

  async executeTransformation(params: ExecutionParams): Promise<ExecutionResult> {
    const response = await axios.post(`${this.baseUrl}/api/execute`, {
      transformation: params.transformation,
      input: params.input,
      inputFormat: params.inputFormat
    });
    return response.data;
  }

  async parseSchema(schema: string, format: string): Promise<ParsedSchema> {
    const response = await axios.post(`${this.baseUrl}/api/parse-schema`, {
      schema,
      format
    });
    return response.data;
  }
}
```

---

## What's Missing: REST API in Daemon

### Current State

**Daemon currently ONLY supports LSP/JSON-RPC:**

```kotlin
// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt

private fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
    return try {
        when (request.method) {
            // LSP lifecycle
            "initialize" -> handleInitialize(request)
            "shutdown" -> handleShutdown(request)

            // LSP text document methods
            "textDocument/didOpen" -> handleDidOpen(request)
            "textDocument/didChange" -> handleDidChange(request)
            "textDocument/completion" -> handleCompletion(request)
            "textDocument/hover" -> handleHover(request)

            // Custom UTLX methods (still LSP protocol)
            "utlx/setMode" -> handleSetMode(request)
            "utlx/inferOutputSchema" -> handleInferOutputSchema(request)

            else -> JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(-32601, "Method not found: ${request.method}")
            )
        }
    } catch (e: Exception) {
        // Error handling...
    }
}
```

**Problem:** MCP server cannot use LSP protocol (wrong abstraction level).

---

### What Needs to be Added: HTTP/REST API Server

**Add Express/Ktor HTTP server alongside LSP server:**

```kotlin
// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/api/RestApiServer.kt

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class RestApiServer(
    private val port: Int = 7778,
    private val stateManager: StateManager,
    private val validator: TransformationValidator,
    private val executor: TransformationExecutor,
    private val schemaParser: SchemaParser
) {

    private var server: ApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            routing {
                // Validation endpoint
                post("/api/validate") {
                    val request = call.receive<ValidateRequest>()
                    val result = validator.validate(request.transformation)
                    call.respond(HttpStatusCode.OK, result)
                }

                // Type inference endpoint
                post("/api/infer-schema") {
                    val request = call.receive<InferSchemaRequest>()
                    val result = inferOutputSchema(
                        transformation = request.transformation,
                        inputSchema = request.inputSchema,
                        inputFormat = request.inputFormat
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                // Execution endpoint
                post("/api/execute") {
                    val request = call.receive<ExecuteRequest>()
                    val result = executor.execute(
                        transformation = request.transformation,
                        input = request.input,
                        inputFormat = request.inputFormat
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                // Schema parsing endpoint
                post("/api/parse-schema") {
                    val request = call.receive<ParseSchemaRequest>()
                    val result = schemaParser.parse(
                        schema = request.schema,
                        format = request.format
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                // Health check
                get("/api/health") {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "UP",
                        "version" to "1.0.0"
                    ))
                }
            }
        }.start(wait = false)

        logger.info("REST API Server started on port $port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        logger.info("REST API Server stopped")
    }
}

// Request/Response DTOs
@Serializable
data class ValidateRequest(val transformation: String)

@Serializable
data class InferSchemaRequest(
    val transformation: String,
    val inputSchema: String,
    val inputFormat: String
)

@Serializable
data class ExecuteRequest(
    val transformation: String,
    val input: String,
    val inputFormat: String
)

@Serializable
data class ParseSchemaRequest(
    val schema: String,
    val format: String
)
```

---

### Updated Daemon Startup: Both LSP and REST API

```kotlin
// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt

class UTLXDaemon(
    private val lspTransportType: TransportType = TransportType.STDIO,
    private val lspPort: Int = 7777,
    private val restApiPort: Int = 7778,
    private val enableRestApi: Boolean = true
) {

    private val stateManager = StateManager()
    private val validator = TransformationValidator(stateManager)
    private val executor = TransformationExecutor(stateManager)
    private val schemaParser = SchemaParser()

    private var lspTransport: Transport? = null
    private var restApiServer: RestApiServer? = null

    fun start() {
        logger.info("Starting UTL-X Daemon")

        // Start LSP server (for Theia Monaco Editor)
        lspTransport = when (lspTransportType) {
            TransportType.STDIO -> StdioTransport()
            TransportType.SOCKET -> SocketTransport(lspPort)
        }
        lspTransport!!.start { request -> handleLSPRequest(request) }
        logger.info("LSP Server started (transport: $lspTransportType)")

        // Start REST API server (for MCP Server)
        if (enableRestApi) {
            restApiServer = RestApiServer(
                port = restApiPort,
                stateManager = stateManager,
                validator = validator,
                executor = executor,
                schemaParser = schemaParser
            )
            restApiServer!!.start()
            logger.info("REST API Server started (port: $restApiPort)")
        }

        logger.info("UTL-X Daemon ready")
    }

    fun stop() {
        logger.info("Stopping UTL-X Daemon")
        lspTransport?.stop()
        restApiServer?.stop()
    }
}
```

---

## Summary: Number of Sessions and Protocols

### Sessions to Daemon

**ONE LSP session** (from Theia backend):
- Protocol: LSP JSON-RPC 2.0
- Transport: STDIO or Socket (your choice)
- Purpose: Code intelligence (autocomplete, hover, diagnostics, mode switching)
- Client: Theia backend (Node.js)

**N REST API sessions** (from MCP server and potentially other clients):
- Protocol: HTTP/REST
- Transport: HTTP (stateless)
- Purpose: Validation, execution, schema analysis, type inference
- Clients: MCP server, CI/CD tools, testing frameworks

**These are DIFFERENT protocols and INDEPENDENT sessions.**

---

## Required Changes to Current UTL-X Daemon

### ✅ No Changes Needed

1. **LSP protocol implementation** - Already correct
2. **STDIO/Socket transport** - Already supports both
3. **Core services** (parser, validator, executor, type checker) - Already exist

### ⚠️ Changes Required

1. **ADD:** HTTP/REST API server (Ktor or similar)
2. **ADD:** REST API endpoints:
   - `POST /api/validate` - Validate UTLX transformation
   - `POST /api/execute` - Execute transformation
   - `POST /api/infer-schema` - Infer output schema
   - `POST /api/parse-schema` - Parse input schema
   - `GET /api/health` - Health check
3. **MODIFY:** Daemon startup to run BOTH LSP and REST API servers
4. **ADD:** Configuration for REST API port (default: 7778)

### Implementation Estimate

- **Complexity:** Low-Medium
- **Time:** 2-3 days
- **LOC:** ~300-500 lines (REST API server + endpoints)
- **Dependencies:** Ktor or Javalin (lightweight HTTP server)

---

## Recommended Approach

### Option A: Add REST API to Existing Daemon (Recommended)

**Pros:**
- ✅ Share all core services (parser, validator, executor)
- ✅ Single process to manage
- ✅ No code duplication
- ✅ Consistent state management

**Cons:**
- ⚠️ One process handles two protocols
- ⚠️ Need to add HTTP server dependency

### Option B: Create Separate HTTP Service

**Pros:**
- ✅ Separation of concerns
- ✅ Can scale independently

**Cons:**
- ❌ Code duplication (need to replicate core services)
- ❌ Two processes to manage
- ❌ State synchronization issues
- ❌ More complex deployment

**Verdict:** **Option A** is clearly better.

---

## Architecture Decision Record

**Decision:** Add REST API server to existing UTL-X Daemon

**Rationale:**
1. MCP server needs programmatic access to daemon capabilities
2. LSP protocol is NOT appropriate for non-editor clients
3. REST API is standard for service-to-service communication
4. Sharing core services avoids duplication and ensures consistency

**Implementation:**
- Add Ktor HTTP server to daemon
- Expose validation, execution, and schema APIs via REST
- Run LSP and REST API servers concurrently in same process
- Use different ports (7777 for LSP, 7778 for REST API)

**Impact:**
- Low risk (additive change only)
- High value (enables MCP integration and other tooling)
- Minimal code (300-500 LOC)

---

## Next Steps

1. **Add Ktor dependency** to `modules/daemon/build.gradle.kts`
2. **Create REST API server** (`RestApiServer.kt`)
3. **Define DTOs** for request/response
4. **Implement endpoints** (validate, execute, infer, parse)
5. **Update daemon startup** to launch both servers
6. **Add integration tests** for REST API
7. **Update MCP tools** to use REST API instead of (non-existent) LSP access
8. **Document REST API** (OpenAPI/Swagger spec)

---

## Conclusion

**Your understanding was partially correct:**
- ✅ Theia will connect to daemon via LSP
- ❌ MCP does NOT connect to LSP - it uses a separate REST API

**No changes needed to LSP:**
- ✅ Current STDIO/Socket transport is perfect
- ✅ LSP protocol implementation is correct

**Changes needed:**
- ⚠️ ADD REST API server to daemon for MCP access
- ⚠️ Expose daemon capabilities via HTTP endpoints
- ⚠️ Run both LSP and REST API servers concurrently

**Bottom line:** You built the right foundation. Now we just need to add a second interface (REST API) to expose the same capabilities to non-LSP clients like the MCP server.

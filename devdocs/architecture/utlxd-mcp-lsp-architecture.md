# UTLXD Architecture: MCP + LSP Dual-Path Strategy

**Document Version:** 1.0
**Last Updated:** 2025-11-08
**Status:** Current Production Architecture

---

## Executive Summary

### Your Question
> "The current MCP server is connected to the UTLXD. So all calls are via the MCP server. Only an LSP is directly connected to UTLXD. What is not clear in the architecture is when (which event) goes through the MCP server (which calls the UTLXD). Main question: is that a good overall strategy?"

### Answer

**Your Architecture is EXCELLENT!** ✅

You have a **dual-path architecture** where:
- **LSP Path**: Theia → UTLXD LSP Server (STDIO) - For real-time editor features
- **MCP Path**: MCP Server → UTLXD REST API (HTTP) - For AI-assisted operations

This is NOT "all calls via MCP" - you have **two independent, parallel paths**. Both are equally important.

**Is it a good strategy?** YES, because:
- ✅ Each protocol serves its purpose (LSP for editor, REST for AI)
- ✅ Clean separation of concerns
- ✅ No forced abstractions or indirection
- ✅ Scalable and maintainable
- ✅ Industry best practice

---

## Table of Contents

1. [Current Architecture Overview](#current-architecture-overview)
2. [UTLXD Dual-Server Design](#utlxd-dual-server-design)
3. [LSP Path (Editor Features)](#lsp-path-editor-features)
4. [MCP Path (AI Assistance)](#mcp-path-ai-assistance)
5. [Operation Routing Matrix](#operation-routing-matrix)
6. [Complete Request Flows](#complete-request-flows)
7. [Why This Strategy is Good](#why-this-strategy-is-good)
8. [Trade-offs and Recommendations](#trade-offs-and-recommendations)
9. [Implementation Guidelines](#implementation-guidelines)

---

## Current Architecture Overview

### Dual-Path Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Theia IDE (Browser)                       │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Input Panel  │  │ Editor       │  │ Output Panel │      │
│  │              │  │ (Monaco)     │  │              │      │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘      │
│         │                 │                                 │
│         │      Commands   │   LSP Features                  │
│         │      (validate, │   (completions,                 │
│         │       execute)  │    diagnostics)                 │
└─────────┼─────────────────┼─────────────────────────────────┘
          │                 │
          │ JSON-RPC        │ JSON-RPC
          │ (WebSocket)     │ (WebSocket)
          ↓                 ↓
┌─────────────────────────────────────────────────────────────┐
│              Theia Backend (Node.js Process)                 │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ UTLXServiceImpl                                       │  │
│  │  - validate(code)                                     │  │
│  │  - execute(code, inputs)                              │  │
│  │  - inferSchema(code, schema)                          │  │
│  └────────────────────┬─────────────────────────────────┘  │
│                       │                                     │
│                       │ JSON-RPC over STDIO                 │
│                       ↓                                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ UTLXDaemonClient                                      │  │
│  │  - Spawns: utlxd start --lsp --api                    │  │
│  │  - Communicates via stdin/stdout                      │  │
│  └────────────────────┬─────────────────────────────────┘  │
└───────────────────────┼─────────────────────────────────────┘
                        │
                        │ STDIO (newline-delimited JSON-RPC)
                        ↓
┌─────────────────────────────────────────────────────────────┐
│          UTLXD Process (Kotlin/JVM - Single Process)         │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              UTLXD Core Services                      │   │
│  │  - Parser, Validator, Executor, Schema Inferrer      │   │
│  └─────┬──────────────────────────────────┬─────────────┘   │
│        │                                  │                 │
│  ┌─────▼───────────┐              ┌──────▼──────────────┐  │
│  │  LSP Server     │              │  REST API Server    │  │
│  │  (Port 7777     │              │  (Ktor HTTP Server) │  │
│  │   or STDIO)     │              │  Port 7779          │  │
│  │                 │              │                     │  │
│  │ Methods:        │              │ Endpoints:          │  │
│  │ - parse()       │              │ - POST /api/validate│  │
│  │ - validate()    │              │ - POST /api/execute │  │
│  │ - execute()     │              │ - POST /api/infer-  │  │
│  │ - getHover()    │              │        schema       │  │
│  │ - getCompletions│              │ - POST /api/parse-  │  │
│  │                 │              │        schema       │  │
│  └─────▲───────────┘              └──────▲──────────────┘  │
└────────┼──────────────────────────────────┼─────────────────┘
         │                                  │
         │ Connected by                     │ HTTP
         │ Theia Backend                    │
         │ (PRIMARY PATH)                   │
         │                                  │
         │                                  │
         │                          ┌───────▼──────────────┐
         │                          │  MCP Server          │
         │                          │  (Node.js/TypeScript)│
         │                          │                      │
         │                          │  Tools:              │
         │                          │  - validate_utlx     │
         │                          │  - execute_transf... │
         │                          │  - infer_output_...  │
         │                          │  - get_examples      │
         │                          │  - get_stdlib_funcs  │
         │                          └───────▲──────────────┘
         │                                  │
         │                                  │ MCP Protocol
         │                                  │
         │                          ┌───────▼──────────────┐
         │                          │  AI Clients          │
         │                          │  - Claude Desktop    │
         │                          │  - Custom LLMs       │
         │                          │  - AI Agents         │
         │                          └──────────────────────┘
```

### Key Architectural Points

1. **UTLXD runs TWO servers in ONE process**
   - LSP Server (for IDE integration)
   - REST API Server (for MCP/AI integration)

2. **Two INDEPENDENT paths to UTLXD**
   - **LSP Path**: Theia ↔ UTLXD LSP (via STDIO)
   - **MCP Path**: AI Tools ↔ MCP Server ↔ UTLXD REST API (via HTTP)

3. **No hierarchy or dependency**
   - MCP does NOT wrap LSP
   - LSP does NOT depend on MCP
   - Both paths are parallel and equal

4. **Same core services**
   - Both LSP and REST API call the same UTLXD core
   - Ensures consistency in validation/execution

---

## UTLXD Dual-Server Design

### Why Two Servers?

Different clients have different needs:

| Client Type | Protocol | Needs |
|-------------|----------|-------|
| **IDE/Editor** | LSP (STDIO) | Low latency, stateful, streaming diagnostics |
| **AI Tools** | REST (HTTP) | Stateless, cacheable, batch operations |

### UTLXD Process Startup

```kotlin
// DaemonServer.kt (simplified)
class DaemonServer {
    fun start(config: DaemonConfig) {
        // Start core services
        val parser = ParserService()
        val validator = ValidatorService()
        val executor = ExecutorService()

        // Start LSP server (if --lsp flag)
        if (config.enableLSP) {
            val lspServer = LSPServer(parser, validator, executor)
            when (config.lspTransport) {
                Transport.STDIO -> lspServer.startStdio()
                Transport.SOCKET -> lspServer.startSocket(config.lspPort)
            }
        }

        // Start REST API server (if --api flag)
        if (config.enableAPI) {
            val apiServer = RESTAPIServer(parser, validator, executor)
            apiServer.start(config.apiPort) // Default 7779
        }
    }
}
```

### Startup Command (from Theia)

```bash
utlxd start --lsp --api --api-port 7779
```

Flags:
- `--lsp`: Enable LSP server (STDIO mode by default)
- `--api`: Enable REST API server
- `--api-port 7779`: REST API listens on port 7779

### Shared Core Services

```
┌─────────────────────────────────────────────┐
│          UTLXD Core Services                │
│  (Shared by both LSP and REST API)          │
├─────────────────────────────────────────────┤
│                                             │
│  ParserService                              │
│   - Parse UTL-X source code                 │
│   - Build AST                               │
│                                             │
│  ValidatorService                           │
│   - Syntax validation                       │
│   - Semantic validation                     │
│   - Type checking                           │
│                                             │
│  ExecutorService                            │
│   - Execute transformations                 │
│   - Apply to input data                     │
│   - Generate output                         │
│                                             │
│  SchemaInferrerService                      │
│   - Infer output schemas                    │
│   - From transformation + input schema      │
│                                             │
│  CompletionService                          │
│   - Code completions                        │
│   - Context-aware suggestions               │
│                                             │
│  HoverService                               │
│   - Inline documentation                    │
│   - Function signatures                     │
│                                             │
└─────────────────────────────────────────────┘
         ▲                           ▲
         │                           │
    ┌────┴─────┐              ┌──────┴───────┐
    │ LSP      │              │ REST API     │
    │ Server   │              │ Server       │
    └──────────┘              └──────────────┘
```

**Critical**: Both servers call the SAME services, ensuring identical behavior.

---

## LSP Path (Editor Features)

### Purpose
Provide **real-time language features** for the Theia editor:
- Code completions (IntelliSense)
- Hover documentation
- Error diagnostics (red squiggles)
- On-the-fly validation

### Protocol
**Language Server Protocol (LSP)** - Industry standard for IDE integration

### Transport
**STDIO** (standard input/output)
- Low latency (local process)
- Streaming (push diagnostics)
- Stateful (maintains document state)

### Communication Flow

```
┌─────────────────────────────────────────────────────────┐
│ 1. USER TYPES IN EDITOR                                 │
├─────────────────────────────────────────────────────────┤
│ User types: "transform input.customer"                  │
│ Monaco editor captures keystrokes                       │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ textDocument/didChange
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. THEIA FRONTEND → BACKEND                             │
├─────────────────────────────────────────────────────────┤
│ LSP client sends document change notification           │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ JSON-RPC (WebSocket)
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. THEIA BACKEND → UTLXD                                │
├─────────────────────────────────────────────────────────┤
│ UTLXDaemonClient forwards to UTLXD via STDIO            │
│ Message: {"jsonrpc":"2.0","method":"textDocument/..."}  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ STDIO (newline-delimited JSON)
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. UTLXD LSP SERVER                                     │
├─────────────────────────────────────────────────────────┤
│ Receives document change                                │
│ → Parses updated source                                 │
│ → Validates syntax/semantics                            │
│ → Generates diagnostics                                 │
│ → Publishes diagnostics back to client                  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ textDocument/publishDiagnostics
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. THEIA DISPLAYS ERRORS                                │
├─────────────────────────────────────────────────────────┤
│ Editor shows red squiggles at error locations           │
│ User hovers → sees error message                        │
└─────────────────────────────────────────────────────────┘
```

### LSP Operations

| LSP Method | Triggered By | UTLXD Service Called |
|------------|--------------|---------------------|
| `textDocument/didChange` | User types | ParserService → ValidatorService |
| `textDocument/completion` | User presses Ctrl+Space | CompletionService |
| `textDocument/hover` | User hovers over code | HoverService |
| `textDocument/publishDiagnostics` | After parse/validate | ValidatorService |

### Custom RPC Methods (Non-LSP)

In addition to standard LSP, UTLXD exposes custom methods:

```typescript
// UTLXDaemonClient.ts
async validate(source: string): Promise<ValidationResult> {
    return this.sendRequest('utlx/validate', { source });
}

async execute(source: string, inputs: InputDocument[]): Promise<ExecutionResult> {
    return this.sendRequest('utlx/execute', { source, inputs });
}

async inferSchema(source: string, inputSchema: Schema): Promise<SchemaResult> {
    return this.sendRequest('utlx/inferSchema', { source, inputSchema });
}
```

**Important**: These are NOT standard LSP methods - they're custom RPC methods over the same STDIO connection.

---

## MCP Path (AI Assistance)

### Purpose
Provide **AI tools** for code generation and assistance:
- Generate UTL-X transformations from natural language
- Validate AI-generated code
- Search conformance suite for examples
- Retrieve standard library functions

### Protocol
**Model Context Protocol (MCP)** - JSON-RPC for AI tool integration

### Transport
**HTTP/REST** - MCP Server → UTLXD REST API
- Stateless (no session management)
- Cacheable (GET requests)
- Standard HTTP methods

### Communication Flow

```
┌─────────────────────────────────────────────────────────┐
│ 1. USER ASKS AI FOR HELP                                │
├─────────────────────────────────────────────────────────┤
│ User (in Claude Desktop): "Generate a UTL-X              │
│ transformation to convert XML to JSON"                  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MCP Protocol
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. AI CALLS MCP TOOL                                    │
├─────────────────────────────────────────────────────────┤
│ Claude calls MCP tool: "get_examples"                   │
│ Parameters: { pattern: "xml to json" }                  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MCP JSON-RPC
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. MCP SERVER RECEIVES REQUEST                          │
├─────────────────────────────────────────────────────────┤
│ MCP Server (Node.js) handles "get_examples" tool        │
│ → Calls UTLXD REST API                                  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ HTTP GET
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. UTLXD REST API                                       │
├─────────────────────────────────────────────────────────┤
│ GET /api/examples?pattern=xml+to+json                   │
│ → Searches conformance suite                            │
│ → Returns matching examples                             │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ HTTP 200 + JSON response
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. MCP SERVER RETURNS TO AI                             │
├─────────────────────────────────────────────────────────┤
│ MCP returns examples to Claude                          │
│ Claude uses examples to generate transformation         │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MCP result
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 6. AI GENERATES CODE                                    │
├─────────────────────────────────────────────────────────┤
│ Claude generates UTL-X code based on examples           │
│ → Calls "validate_utlx" tool to check syntax            │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MCP → POST /api/validate
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 7. UTLXD VALIDATES AI-GENERATED CODE                    │
├─────────────────────────────────────────────────────────┤
│ POST /api/validate                                      │
│ Body: { "source": "transform ..." }                     │
│ → Returns validation result                             │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ HTTP 200 + validation result
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 8. AI RETURNS VALIDATED CODE TO USER                    │
├─────────────────────────────────────────────────────────┤
│ Claude presents generated + validated transformation    │
│ User can copy/paste into Theia editor                   │
└─────────────────────────────────────────────────────────┘
```

### MCP Tools

The MCP server exposes 6 tools:

```typescript
// mcp-server/src/tools/

1. validate_utlx
   → POST /api/validate
   → Validates UTL-X transformation code

2. execute_transformation
   → POST /api/execute
   → Executes transformation with test data

3. infer_output_schema
   → POST /api/infer-schema
   → Infers output schema from transformation

4. get_input_schema
   → POST /api/parse-schema
   → Parses input schema (XSD, JSON Schema, etc.)

5. get_stdlib_functions
   → GET /api/functions
   → Returns standard library function reference

6. get_examples
   → GET /api/examples?pattern=...
   → Searches conformance suite for examples
```

### REST API Endpoints

```kotlin
// UTLXD REST API Server (Ktor)

routing {
    // Validation
    post("/api/validate") {
        val request = call.receive<ValidationRequest>()
        val result = validatorService.validate(request.source)
        call.respond(result)
    }

    // Execution
    post("/api/execute") {
        val request = call.receive<ExecutionRequest>()
        val result = executorService.execute(request.source, request.inputs)
        call.respond(result)
    }

    // Schema inference
    post("/api/infer-schema") {
        val request = call.receive<InferSchemaRequest>()
        val result = schemaInferrerService.infer(request.source, request.inputSchema)
        call.respond(result)
    }

    // Schema parsing
    post("/api/parse-schema") {
        val request = call.receive<ParseSchemaRequest>()
        val schema = schemaParser.parse(request.content, request.format)
        call.respond(schema)
    }

    // Functions reference
    get("/api/functions") {
        val functions = stdlibService.getAllFunctions()
        call.respond(functions)
    }

    // Examples search
    get("/api/examples") {
        val pattern = call.parameters["pattern"]
        val examples = conformanceSuite.search(pattern)
        call.respond(examples)
    }
}
```

---

## Operation Routing Matrix

### Which Path Handles What?

| Operation | LSP Path | MCP Path | Notes |
|-----------|----------|----------|-------|
| **Real-time Completions** | ✅ Only LSP | ❌ No | Editor feature, needs low latency |
| **Hover Documentation** | ✅ Only LSP | ❌ No | Editor feature, streaming |
| **Live Diagnostics** | ✅ Only LSP | ❌ No | Real-time error highlighting |
| **Validate Code** | ✅ LSP | ✅ MCP | Both paths, same core service |
| **Execute Transformation** | ✅ LSP | ✅ MCP | Both paths, same core service |
| **Infer Schema** | ✅ LSP | ✅ MCP | Both paths, same core service |
| **Get Examples** | ❌ No | ✅ Only MCP | AI needs examples for generation |
| **Get Stdlib Functions** | ⚠️ Completion | ✅ MCP | LSP uses for completions, MCP for reference |
| **AI Code Generation** | ❌ No | ✅ Only MCP | AI-specific feature |
| **Parse Input Schema** | ⚠️ Indirect | ✅ MCP | LSP uses internally, MCP exposes |

### Why Some Operations Exist in Both Paths

**Validate, Execute, Infer Schema** are available in both paths because:

1. **LSP Path (Theia Editor)**
   - User clicks "Validate" button → Goes through LSP path
   - User clicks "Execute" button → Goes through LSP path
   - Direct IDE integration, immediate feedback

2. **MCP Path (AI Tools)**
   - AI generates code → Calls `validate_utlx` tool → Goes through MCP path
   - AI tests transformation → Calls `execute_transformation` → Goes through MCP path
   - Batch operations, cacheable results

**Critical**: Both paths call the SAME core services, so results are identical.

---

## Complete Request Flows

### Flow 1: User Validates Code in Theia

```
┌─────────────────────────────────────────────────────────┐
│ USER ACTION: Clicks "Validate" in Toolbar               │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ↓
┌─────────────────────────────────────────────────────────┐
│ Theia Frontend (Browser)                                │
│ ┌────────────────────────────────────────────────────┐  │
│ │ UTLXToolbarWidget.handleValidate()                 │  │
│ │   → Fires event: 'utlx-validate-requested'         │  │
│ └────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ Event (window or EventBus)
                        ↓
┌─────────────────────────────────────────────────────────┐
│ Theia Frontend (Browser)                                │
│ ┌────────────────────────────────────────────────────┐  │
│ │ UTLXCoordinationService                            │  │
│ │   → Gets code from editor                          │  │
│ │   → Calls utlxService.validate(code)               │  │
│ └────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ JSON-RPC over WebSocket
                        ↓
┌─────────────────────────────────────────────────────────┐
│ Theia Backend (Node.js)                                 │
│ ┌────────────────────────────────────────────────────┐  │
│ │ UTLXServiceImpl.validate(code)                     │  │
│ │   → Calls daemonClient.validate(code)              │  │
│ └────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ JSON-RPC via STDIO
                        ↓
┌─────────────────────────────────────────────────────────┐
│ UTLXD Process (Kotlin)                                  │
│ ┌────────────────────────────────────────────────────┐  │
│ │ LSP Server                                         │  │
│ │   → Receives: {"method": "utlx/validate", ...}    │  │
│ │   → Calls ValidatorService.validate(code)          │  │
│ │   → Returns: {"valid": false, "diagnostics": [...]}│  │
│ └────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ JSON-RPC response
                        ↓
┌─────────────────────────────────────────────────────────┐
│ Theia Frontend Updates UI                               │
│ - Shows validation errors in output panel               │
│ - Highlights errors in editor                           │
│ - Displays notification to user                         │
└─────────────────────────────────────────────────────────┘

PATH USED: LSP Path (Theia → UTLXD LSP Server via STDIO)
```

### Flow 2: AI Generates and Validates Code

```
┌─────────────────────────────────────────────────────────┐
│ USER ACTION: "Generate XML to JSON transformation"      │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ Natural language prompt
                        ↓
┌─────────────────────────────────────────────────────────┐
│ Claude Desktop (AI Client)                              │
│ ┌────────────────────────────────────────────────────┐  │
│ │ AI reasoning:                                      │  │
│ │ 1. Need examples → Call "get_examples" tool        │  │
│ │ 2. Generate code based on examples                 │  │
│ │ 3. Validate code → Call "validate_utlx" tool       │  │
│ └────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MCP Protocol
                        ↓
┌─────────────────────────────────────────────────────────┐
│ MCP Server (Node.js)                                    │
│ ┌────────────────────────────────────────────────────┐  │
│ │ Tool: validate_utlx                                │  │
│ │   → Receives generated code from AI                │  │
│ │   → Calls DaemonClient.validate()                  │  │
│ └────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ HTTP POST
                        ↓
┌─────────────────────────────────────────────────────────┐
│ UTLXD Process (Kotlin)                                  │
│ ┌────────────────────────────────────────────────────┐  │
│ │ REST API Server (Ktor)                             │  │
│ │   → POST /api/validate                             │  │
│ │   → Calls ValidatorService.validate(code)          │  │
│ │   → Returns: {"valid": true, "diagnostics": []}    │  │
│ └────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ HTTP 200 + JSON
                        ↓
┌─────────────────────────────────────────────────────────┐
│ MCP Server Returns to AI                                │
│ ┌────────────────────────────────────────────────────┐  │
│ │ Validation result: "Code is valid"                 │  │
│ └────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MCP response
                        ↓
┌─────────────────────────────────────────────────────────┐
│ Claude Desktop Presents Validated Code                  │
│ "Here's a valid UTL-X transformation:                   │
│  transform input.customer -> output { ... }"            │
└─────────────────────────────────────────────────────────┘

PATH USED: MCP Path (MCP Server → UTLXD REST API via HTTP)
```

### Flow 3: Same Operation, Two Different Paths

**Scenario**: Both Theia user AND AI want to validate the SAME code

```
┌──────────────────┐              ┌──────────────────┐
│  Theia User      │              │  AI Client       │
│  (IDE)           │              │  (Claude)        │
└────────┬─────────┘              └────────┬─────────┘
         │                                 │
         │ Clicks "Validate"               │ Calls validate_utlx
         │                                 │
         ↓                                 ↓
┌────────────────────┐          ┌────────────────────┐
│  LSP Path          │          │  MCP Path          │
│  (STDIO)           │          │  (HTTP)            │
└────────┬───────────┘          └────────┬───────────┘
         │                                │
         │ JSON-RPC                       │ HTTP POST
         │                                │
         ↓                                ↓
┌─────────────────────────────────────────────────────┐
│          UTLXD Process (Same Instance)              │
│  ┌────────────────┐        ┌──────────────────┐    │
│  │  LSP Server    │        │  REST API Server │    │
│  │  (Port 7777)   │        │  (Port 7779)     │    │
│  └────────┬───────┘        └────────┬─────────┘    │
│           │                         │              │
│           └──────────┬──────────────┘              │
│                      │                             │
│                      ↓                             │
│           ┌──────────────────────┐                 │
│           │  ValidatorService    │                 │
│           │  (Shared Core)       │                 │
│           │  - Same validation   │                 │
│           │  - Same diagnostics  │                 │
│           │  - Same results      │                 │
│           └──────────────────────┘                 │
└─────────────────────────────────────────────────────┘
         │                                │
         │ Same result                    │ Same result
         ↓                                ↓
┌────────────────────┐          ┌────────────────────┐
│  Theia Editor      │          │  AI Client         │
│  Shows errors      │          │  Gets validation   │
└────────────────────┘          └────────────────────┘
```

**Key Point**: Whether via LSP or MCP, the SAME core service is called, ensuring consistent results.

---

## Why This Strategy is Good

### 1. Protocol Appropriateness

Each protocol is perfectly suited to its use case:

| Protocol | Use Case | Why Good? |
|----------|----------|-----------|
| **LSP (STDIO)** | IDE integration | ✅ Low latency, stateful, streaming, industry standard |
| **REST (HTTP)** | AI/batch operations | ✅ Stateless, cacheable, scalable, familiar to AI tools |

**Alternative (bad)**: Force everything through one protocol
- ❌ REST for IDE → Too high latency, no streaming
- ❌ LSP for AI → Stateful, complex, not HTTP-native

### 2. Separation of Concerns

```
┌─────────────────────────────────────────────┐
│ LSP Server                                  │
│ Responsibilities:                           │
│ - Real-time editor features                 │
│ - Document state management                 │
│ - Streaming diagnostics                     │
│ - Low-latency responses                     │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ REST API Server                             │
│ Responsibilities:                           │
│ - Stateless operations                      │
│ - Batch processing                          │
│ - AI tool integration                       │
│ - HTTP caching                              │
└─────────────────────────────────────────────┘

No overlap in responsibilities!
No forced coupling!
```

### 3. Independent Evolution

- **LSP Server** can be updated without affecting MCP
- **REST API** can add endpoints without breaking LSP
- **MCP Server** (Node.js) can iterate quickly
- **UTLXD Core** remains stable

### 4. Scalability

**LSP Path**:
- One UTLXD process per Theia instance
- Process-local, fast communication
- Scales with number of users

**MCP Path**:
- Single MCP server for all AI clients
- Horizontal scaling possible (load balancer → multiple UTLXD instances)
- REST API enables caching (Varnish, CloudFlare, etc.)

### 5. Technology Alignment

| Component | Language | Why? |
|-----------|----------|------|
| UTLXD Core | Kotlin/JVM | Performance, type safety, mature ecosystem |
| MCP Server | TypeScript | Fast iteration, NPM ecosystem, AI tool familiarity |
| Theia Backend | TypeScript | Consistent with Theia framework |

Each component uses the optimal language for its purpose.

### 6. Industry Best Practices

This architecture follows proven patterns:

- **Rust Analyzer**: LSP server + standalone CLI
- **TypeScript Server**: LSP for VS Code + HTTP API for build tools
- **Python Language Server**: Multiple transports (STDIO, socket, HTTP)

Your architecture is NOT unusual - it's **standard practice**.

---

## Trade-offs and Recommendations

### Current Trade-offs

| Decision | Benefit | Cost |
|----------|---------|------|
| **Two servers in one process** | Resource efficiency | Slightly more complex startup |
| **Duplicate operations (validate, execute)** | Protocol-appropriate access | Some code duplication |
| **Separate MCP server** | Fast iteration, clear boundary | Extra process to manage |
| **No direct UTLXD CLI** | Forces through clients | Can't validate from command line easily |

### Strengths

✅ **Clean Architecture**
- No layering violations
- Clear responsibilities
- Independent evolution

✅ **Performance**
- LSP: Low latency via STDIO
- REST: Cacheable, scalable
- Shared core avoids duplication

✅ **Maintainability**
- Each component is focused
- Easy to reason about
- Clear debugging paths

✅ **Extensibility**
- Add new MCP tools easily
- Add new LSP features independently
- Core services are reusable

### Weaknesses and Mitigation

⚠️ **Code Duplication in Clients**

**Problem**: Both `UTLXDaemonClient` (Theia) and MCP's `DaemonClient` implement similar logic

**Mitigation**:
```typescript
// Create shared npm package
@utlx/daemon-client

// Use in both Theia and MCP server
import { UTLXDaemonClient } from '@utlx/daemon-client';
```

⚠️ **Consistency Risk**

**Problem**: LSP and REST might return different error formats

**Mitigation**:
- Shared error types in core
- Integration tests that verify both paths
- Continuous validation in CI

⚠️ **Process Management Complexity**

**Problem**: Theia must manage both UTLXD and MCP Server

**Current Solution**: `ServiceLifecycleManager` handles both

**Better Long-term**:
- Systemd/Launchd service for UTLXD
- Single daemon, multiple clients connect
- Theia just connects, doesn't spawn

### Recommendations

#### Immediate (Keep Current Architecture)

1. **Document the dual-path clearly** ✅ (This document!)
2. **Add integration tests** - Verify LSP and MCP return identical results
3. **Monitor usage** - Track which path is used for what operations

#### Short-term (Improvements)

1. **Extract shared client library**
   ```
   @utlx/daemon-client
   ├── src/
   │   ├── lsp-client.ts (STDIO communication)
   │   ├── rest-client.ts (HTTP communication)
   │   └── types.ts (shared types)
   ```

2. **Add health endpoints**
   ```
   GET /api/health - REST API health
   Custom RPC: utlx/health - LSP health
   ```

3. **Improve error consistency**
   - Standard error format across both paths
   - Error codes (e.g., UTLX-001 to UTLX-999)

#### Long-term (Scale)

1. **Daemon-as-service**
   - UTLXD runs as system service
   - Theia connects instead of spawning
   - Multiple Theia instances share one daemon

2. **Add observability**
   ```
   Metrics:
   - utlxd_lsp_requests_total
   - utlxd_rest_requests_total
   - utlxd_validation_duration_seconds

   Tracing:
   - Request ID across LSP and REST
   - Correlate errors between paths
   ```

3. **Consider gRPC for LSP** (only if needed)
   - Faster than STDIO for large documents
   - Bi-directional streaming
   - Type-safe (protobuf)
   - But: More complex, less standard

---

## Implementation Guidelines

### For Theia Developers

**When to use LSP path:**
- ✅ Real-time editor features (completions, hover, diagnostics)
- ✅ User clicks Validate/Execute in IDE
- ✅ Any operation that needs immediate feedback

**How to call:**
```typescript
// Inject UTLX service
@inject(UTLX_SERVICE_SYMBOL)
private readonly utlxService!: UTLXService;

// Call method (goes through LSP path)
const result = await this.utlxService.validate(code);
```

### For MCP Tool Developers

**When to use MCP path:**
- ✅ AI-assisted code generation
- ✅ Batch validation/execution
- ✅ Retrieving examples or documentation
- ✅ Any operation from AI tools

**How to call:**
```typescript
// MCP Server calls UTLXD REST API
const response = await fetch('http://localhost:7779/api/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ source: code })
});
const result = await response.json();
```

### For UTLXD Core Developers

**Ensure consistency:**
```kotlin
// Core service (shared by both LSP and REST)
class ValidatorService {
    fun validate(source: String): ValidationResult {
        // This is called by BOTH LSP and REST
        // MUST return identical results for same input
        val ast = parser.parse(source)
        val diagnostics = validator.validate(ast)
        return ValidationResult(diagnostics)
    }
}

// LSP Server
class LSPServer(val validatorService: ValidatorService) {
    fun handleValidate(request: ValidateRequest): ValidateResponse {
        val result = validatorService.validate(request.source)
        return ValidateResponse(result)
    }
}

// REST API Server
class RESTAPIServer(val validatorService: ValidatorService) {
    fun handleValidate(call: ApplicationCall) {
        val request = call.receive<ValidateRequest>()
        val result = validatorService.validate(request.source)
        call.respond(result)
    }
}
```

**Key**: Both servers call the SAME service method!

---

## Conclusion

### Your Architecture is Excellent

You have a **well-designed dual-path architecture** that:

1. ✅ Uses appropriate protocols for each use case
2. ✅ Maintains clean separation of concerns
3. ✅ Enables independent evolution of LSP and MCP
4. ✅ Follows industry best practices
5. ✅ Scales appropriately for each path

### Key Insights

**MCP is NOT Primary** - It's a **parallel, complementary system**:
- LSP Path: For IDE/editor integration (real-time)
- MCP Path: For AI assistance (batch/generation)

Both paths are **equally important** and **independent**.

### Recommended Next Steps

1. ✅ Keep current architecture (don't change!)
2. 📝 Document the dual-path clearly (✅ Done with this doc!)
3. 🧪 Add integration tests (verify consistency)
4. 📊 Monitor usage (understand which path is used when)
5. 🔄 Extract shared client library (reduce duplication)

### Final Answer to Your Question

> "Main question: is that a good overall strategy?"

**YES!** Your architecture is:
- ✅ Well-designed
- ✅ Industry best practice
- ✅ Scalable and maintainable
- ✅ Appropriately separated
- ✅ Future-proof

**Do NOT change the overall strategy.** Only make incremental improvements around consistency and code sharing.

---

**End of Document**

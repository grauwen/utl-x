# UTLXe Engine Redesign — Open-M Integration Plan

**Status:** Implementation Plan  
**Date:** 2026-04-20  
**Relates to:** open-m-go-versus-kotlin-utlx-depends.md, utlxe-engine-architecture.md  
**Branch:** development (`modules/engine/`)

---

## 1. Decision

**Option B adopted:** Go wrapper communicates with UTLXe via stdio-proto (varint-delimited protobuf over stdin/stdout). gRPC over UDS available as alternative mode.

UTLXe becomes a **multi-transport, multi-transformation production engine** that is MPPM-agnostic — it receives named byte payloads and returns transformed byte payloads.

---

## 2. Current State (Phase 1 MVP)

| Aspect | Current | Target |
|--------|---------|--------|
| Transformations | Single (first only) | Multiple concurrent |
| Transport | stdio-json (line-delimited) | stdio-json, stdio-proto, grpc |
| Concurrency | Single-threaded loop | Thread pool with correlation IDs |
| Protocol | Unframed JSON lines | Varint-delimited protobuf |
| IPC mode | N/A (standalone) | Subprocess of Go wrapper |
| Health | HTTP endpoint | HTTP endpoint (unchanged) |
| Strategies | TEMPLATE only | TEMPLATE, COPY (Phase 2) |

---

## 3. Architecture Changes

### 3.1 New File Structure

```
modules/engine/src/main/kotlin/org/apache/utlx/engine/
├── Main.kt                          # MODIFIED: add --mode flag
├── UtlxEngine.kt                    # MODIFIED: multi-transformation, transport-agnostic
├── EngineState.kt                   # UNCHANGED
├── bundle/
│   └── BundleLoader.kt             # UNCHANGED (already discovers multiple)
├── config/
│   ├── EngineConfig.kt             # MINOR: add transport config section
│   └── TransformConfig.kt          # UNCHANGED
├── transport/                       # NEW PACKAGE
│   ├── TransportServer.kt          # NEW: interface for receiving/sending messages
│   ├── StdioJsonTransport.kt       # REFACTORED: extracted from current processMessages()
│   ├── StdioProtoTransport.kt      # NEW: varint-delimited protobuf with multiplexing
│   └── GrpcTransport.kt            # NEW: gRPC server on UDS or TCP
├── pipe/                            # EXISTING (kept for backward compat)
│   ├── InputPipe.kt                # UNCHANGED
│   ├── OutputPipe.kt               # UNCHANGED
│   ├── Message.kt                  # UNCHANGED
│   ├── StdioInputPipe.kt           # UNCHANGED (used by StdioJsonTransport)
│   └── StdioOutputPipe.kt          # UNCHANGED (used by StdioJsonTransport)
├── registry/
│   └── TransformationRegistry.kt   # MODIFIED: support load/unload/get by ID
├── strategy/
│   ├── ExecutionStrategy.kt        # UNCHANGED
│   └── TemplateStrategy.kt         # UNCHANGED
└── health/
    └── HealthEndpoint.kt           # MINOR: add loaded_transformations count
```

### 3.2 Proto Definitions

New file: `proto/utlxe/v1/utlxe.proto`

Contains (as defined in open-m-go-versus-kotlin-utlx-depends.md Section 6.3):
- `StdioEnvelope` (type + payload) — for stdio-proto routing
- `MessageType` enum — request/response type identification
- `LoadTransformationRequest/Response` — init-time compilation
- `ExecuteRequest/Response` — runtime hot-path
- `ExecuteBatchRequest/Response` — batch processing
- `UnloadTransformationRequest/Response` — dynamic removal
- `HealthRequest/Response` — health check
- `ErrorClass` enum — PERMANENT vs TRANSIENT
- `ValidationError`, `LoadMetrics`, `ExecuteMetrics`

gRPC service definition:
- `service UtlxeService { ... }` — for grpc mode only

---

## 4. Implementation Phases

### Phase A: Foundation (Transport Abstraction + Multi-Transformation)

**Goal:** Refactor processMessages() out of UtlxEngine into a transport abstraction. Remove single-transformation limit.

**Files changed:**
- `UtlxEngine.kt` — delegate message loop to TransportServer
- `TransformationRegistry.kt` — add `load()`, `unload()`, `get(id)` methods
- `transport/TransportServer.kt` — new interface
- `transport/StdioJsonTransport.kt` — extracted from current code (backward compatible)

**Tests:** Existing tests continue to pass. New tests for multi-transformation registry.

### Phase B: Proto Definitions + stdio-proto Transport

**Goal:** Add protobuf message definitions and implement stdio-proto mode.

**Files added:**
- `proto/utlxe/v1/utlxe.proto`
- `transport/StdioProtoTransport.kt`

**Key implementation details:**
- Varint-delimited framing using `parseDelimitedFrom()` / `writeDelimitedTo()`
- Reader thread → worker thread pool → response queue → writer thread
- Correlation ID based response matching
- `--mode=stdio-proto` flag in Main.kt

**Dependencies added:**
- `com.google.protobuf:protobuf-kotlin:3.25.x`
- Generated Kotlin stubs from `utlxe.proto`

**Tests:** Integration test: spawn UTLXe in stdio-proto mode, send LoadTransformation + Execute via pipe, verify output.

### Phase C: gRPC Transport

**Goal:** Add gRPC server mode for external consumers and sidecar deployments.

**Files added:**
- `transport/GrpcTransport.kt`

**Key implementation details:**
- `--mode=grpc --socket=/path/to/socket` (UDS)
- `--mode=grpc --address=host:port` (TCP fallback)
- Implements `UtlxeService` from proto definition
- Native HTTP/2 multiplexing (no custom correlation needed)

**Dependencies added:**
- `io.grpc:grpc-kotlin-stub:1.4.x`
- `io.grpc:grpc-netty-shaded:1.60.x`
- `io.grpc:grpc-protobuf:1.60.x`

**Tests:** Integration test with gRPC client calling all methods.

### Phase D: Concurrency Model (Multiplexed stdio-proto)

**Goal:** Enable parallel transform execution within a single UTLXe process.

**Implementation:**
- Worker thread pool (configurable, default = CPU cores)
- `StdioProtoTransport` dispatches to pool, collects via response queue
- Correlation ID in ExecuteResponse matches to ExecuteRequest
- `max_concurrent` per transformation (from LoadTransformationRequest)
- Backpressure: if worker pool is saturated, reader thread blocks

**Tests:** Concurrent requests test, verify out-of-order response matching.

---

## 5. CLI Interface (Main.kt changes)

### Current:
```
utlxe --bundle <path> [--config <path>] [--port <port>] [--validate]
```

### Target:
```
utlxe --bundle <path> [options]            # Bundle mode (Phase 1, backward compat)
utlxe --mode stdio-proto [options]         # Open-M integration mode (no bundle required)
utlxe --mode grpc --socket <path>          # gRPC mode on UDS
utlxe --mode grpc --address <host:port>    # gRPC mode on TCP

Options:
  --bundle, -b <path>       Bundle directory (for bundle-mode startup)
  --config, -c <path>       Engine config file
  --mode <mode>             Transport mode: stdio-json (default), stdio-proto, grpc
  --socket <path>           Unix socket path (gRPC mode)
  --address <host:port>     TCP address (gRPC mode)
  --port, -p <port>         Health endpoint port (default: 8081)
  --workers <n>             Worker thread pool size (default: CPU cores)
  --validate                Load and compile bundle, then exit
  --version, -v             Show version
  --help, -h                Show help
```

### Mode behaviors:

| Mode | Init | Runtime |
|------|------|---------|
| `stdio-json` (default) | Load from bundle | Read JSON lines from stdin, write to stdout |
| `stdio-proto` | Wait for LoadTransformation messages on stdin | Process ExecuteRequest, respond with ExecuteResponse |
| `grpc` | Wait for LoadTransformation RPCs | Process Execute RPCs |

**In stdio-proto/grpc modes, `--bundle` is optional.** Transformations are loaded dynamically via LoadTransformation messages. If `--bundle` is provided, its transformations are pre-loaded at startup (hybrid mode).

---

## 6. TransformationRegistry Changes

### Current:
```kotlin
class TransformationRegistry {
    private val transformations = mutableMapOf<String, TransformationInstance>()
    // Currently only stores one transformation
}
```

### Target:
```kotlin
class TransformationRegistry {
    private val transformations = ConcurrentHashMap<String, TransformationInstance>()

    fun load(id: String, source: String, config: TransformConfig): LoadResult
    fun unload(id: String): Boolean
    fun get(id: String): TransformationInstance?
    fun getAll(): Map<String, TransformationInstance>
    fun count(): Int
}

data class TransformationInstance(
    val id: String,
    val strategy: ExecutionStrategy,
    val config: TransformConfig,
    val loadedAt: Instant,
    val executionCount: AtomicLong = AtomicLong(0),
    val errorCount: AtomicLong = AtomicLong(0)
)

data class LoadResult(
    val success: Boolean,
    val error: String? = null,
    val warnings: List<ValidationError> = emptyList(),
    val metrics: LoadMetrics? = null
)
```

---

## 7. Logging

In stdio-proto mode, stdin/stdout are reserved for the protobuf protocol. All logging (SLF4J/Logback) MUST go to stderr:

```xml
<!-- logback.xml for stdio-proto mode -->
<appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
    <target>System.err</target>
    ...
</appender>
```

The Go wrapper captures UTLXe's stderr and forwards it to the structured log pipeline.

---

## 8. Backward Compatibility

All existing behavior is preserved:

- `utlxe --bundle my-project.utlxp` → works exactly as today (stdio-json, single transformation)
- `utlxe --bundle my-project.utlxp --validate` → works exactly as today
- Health endpoint on same port with same response format
- Existing tests pass without modification

New modes are additive — they don't change default behavior.

---

## 9. Testing Strategy

| Test Level | What | How |
|---|---|---|
| Unit | Registry load/unload/get | Kotlin unit tests |
| Unit | StdioProtoTransport request/response | Mock stdin/stdout with byte arrays |
| Integration | Full lifecycle: spawn → load → execute → shutdown | Process spawn from test, pipe proto messages |
| Integration | gRPC: connect → load → execute → unload | gRPC test client |
| Performance | Throughput under load | Benchmark: N requests/second, measure p50/p99 latency |
| Conformance | Proto compatibility | Go client sends messages, verify Kotlin server processes correctly |

---

## 10. Dependency Impact

| Mode | New dependencies | JAR size impact |
|------|-----------------|-----------------|
| stdio-json | None | 0 |
| stdio-proto | protobuf-kotlin (~2MB) | +2MB |
| grpc | protobuf-kotlin + grpc-kotlin + grpc-netty (~15MB) | +15MB |

Recommendation: Build separate JARs per mode, or use Gradle feature variants to make gRPC dependencies optional.

---

## 11. Open Questions (from design doc)

1. **Subprocess vs sidecar** — Subprocess for stdio-proto (default), sidecar option for gRPC mode
2. **Proto ownership** — `proto/utlxe/v1/utlxe.proto` lives in utl-x repo; Go stubs published as release artifact
3. **GraalVM native for UTLXe** — Future optimization; constrains COMPILED strategy
4. **stderr logging** — Logback to stderr in stdio-proto mode; Go wrapper captures and forwards
5. **Warm-up** — Optional synthetic ExecuteRequest after LoadTransformation for JIT warmup

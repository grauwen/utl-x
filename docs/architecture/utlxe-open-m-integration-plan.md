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

### Phase D: Schema Validation (Pre/Post Transform)

**Goal:** Add engine-level schema validation as orchestration around transformation, using native format-specific validators.

**Files added:**
- `validation/SchemaValidator.kt` — interface (validate payload bytes → List<ValidationError>)
- `validation/SchemaValidatorFactory.kt` — creates validator by schema format
- `validation/JsonSchemaValidator.kt` — JSON Schema via networknt (new dep)
- `validation/XsdValidator.kt` — XSD via javax.xml.validation (JDK built-in)
- `validation/TableSchemaValidator.kt` — TSCH custom (thin)
- `validation/ODataSchemaValidator.kt` — OSCH custom (using parsed EDMX model)
- `validation/AvroSchemaValidator.kt` — Avro via GenericDatumReader (existing dep)
- `validation/ProtobufValidator.kt` — Protobuf via DynamicMessage (existing dep)

**Key implementation:**
- Native format-level validation (NOT UDM comparison) — validates raw payload against compiled schema
- Validators compiled at init time (during LoadTransformation), thread-safe, reusable
- Pre-compiled validators cached in TransformationInstance
- Per-message cost: ~50-500μs depending on format and payload size
- Field-level error messages with JSONPath/XPath to problematic element
- Validation errors returned in ExecuteResponse.validation_errors[]
- Policy enforcement: STRICT (reject → PERMANENT error) / WARN (log, continue) / SKIP (no-op)

**New dependency:** `com.networknt:json-schema-validator:1.5.x` (~500KB, Apache 2.0) for JSON Schema. All others use JDK built-ins or existing UTLXe dependencies.

**Tests:** Validate conforming/non-conforming payloads against JSON Schema, XSD, TSCH, and Avro Schema. Test STRICT/WARN/SKIP policy enforcement.

### Phase E: Concurrency Model (Multiplexed stdio-proto)

**Goal:** Enable parallel transform execution within a single UTLXe process.

**Implementation:**
- Worker thread pool (configurable, default = CPU cores)
- `StdioProtoTransport` dispatches to pool, collects via response queue
- Correlation ID in ExecuteResponse matches to ExecuteRequest
- `max_concurrent` per transformation (from LoadTransformationRequest)
- Backpressure: if worker pool is saturated, reader thread blocks

**Tests:** Concurrent requests test, verify out-of-order response matching.

---

## 5. Schema Validation (Pre/Post Transform)

### 5.1 Design Decision

Validation is an **engine-level orchestration concern** — a pre/post step around the transformation. It is NOT part of the UTL-X language. A `.utlx` script describes structural mapping; validation policy belongs in deployment configuration.

UTLXe already has:
- Full format ecosystem (JSON Schema, XSD, Avro schema validation libraries)
- `validationPolicy` in TransformConfig (STRICT, WARN, SKIP)
- Schema declarations in transform.yaml (input/output schemas)

### 5.2 How Validation Integrates

```
ExecuteRequest arrives
        │
        ▼
  ┌─────────────────────────────┐
  │ PRE-VALIDATION               │  validates input against input schema
  │ (if validate_input = true)   │  using pre-compiled validator
  │                              │
  │ STRICT: reject immediately   │  → PERMANENT error in ExecuteResponse
  │ WARN:   log, continue        │  → validation_errors[] populated
  │ SKIP:   no validation        │  → zero cost
  └─────────────────────────────┘
        │
        ▼
  ┌─────────────────────────────┐
  │ TRANSFORMATION               │  utl-x Program execution
  │ (TEMPLATE/COPY/COMPILED)     │  utl-x knows nothing about validation
  └─────────────────────────────┘
        │
        ▼
  ┌─────────────────────────────┐
  │ POST-VALIDATION              │  validates output against output schema
  │ (if validate_output = true)  │  catches mapping bugs
  └─────────────────────────────┘
        │
        ▼
  ExecuteResponse
    - validation_errors[]: field path + message + severity
    - error_class: PERMANENT (schema violation)
```

### 5.3 Init-Time vs Runtime

**Init time (once, at LoadTransformation):**
- Parse input/output schemas from bundle or from `LoadTransformationRequest.config`
- Compile schemas into pre-compiled validator objects (resolved $ref pointers, compiled type trees)
- Cache validators alongside compiled Program in TransformationInstance
- Schema parse failure → fail fast in LoadTransformationResponse

**Runtime (per message, hot path):**
- Apply pre-compiled validator to payload bytes — no re-parsing
- ~50-200μs for JSON Schema, ~100-500μs for XSD — comparable to transform cost
- Validation errors reported in ExecuteResponse.validation_errors[]

### 5.4 LoadTransformationRequest — Validation via Config Map

Validation settings are passed in the `config` map of `LoadTransformationRequest`, NOT as dedicated proto fields. This keeps the proto contract stable — new validation options are added without changing the proto schema.

```protobuf
message LoadTransformationRequest {
  string transformation_id = 1;
  string utlx_source = 2;           // Can be empty for validate-only mode
  string strategy = 3;              // "TEMPLATE", "COPY", "COMPILED", "AUTO"
  string validation_policy = 4;     // "STRICT", "WARN", "SKIP"
  int32 max_concurrent = 5;
  map<string, string> config = 6;   // Carries all validation settings (see below)
}
```

**Config map keys for validation:**

| Key | Values | Default | Purpose |
|-----|--------|---------|---------|
| `validate_input` | `"true"`, `"false"` | `"false"` | Enable pre-validation |
| `validate_output` | `"true"`, `"false"` | `"false"` | Enable post-validation |
| `input_schema` | Schema source (JSON string, XSD XML, etc.) | — | Input schema content |
| `input_schema_format` | `"json-schema"`, `"xsd"`, `"avro"`, `"protobuf"`, `"tsch"`, `"osch"` | — | Schema format |
| `output_schema` | Schema source | — | Output schema content |
| `output_schema_format` | Same as above | — | Schema format |

**Validate-only mode (no transformation):**

When `utlx_source` is empty but `validate_input` or `validate_output` is true, UTLXe validates the payload without transforming it. The payload passes through unchanged. This supports Open-M connections with `engine: none` but validation enabled.

### 5.5 ExecuteResponse — Error Phase

```protobuf
message ExecuteResponse {
  bool success = 1;
  bytes output = 2;
  string output_content_type = 3;
  string error = 4;                          // Human-readable summary
  ErrorClass error_class = 5;                // PERMANENT or TRANSIENT
  repeated ValidationError validation_errors = 6;
  ExecuteMetrics metrics = 7;
  ErrorPhase error_phase = 8;               // WHERE the error occurred
}

enum ErrorPhase {
  ERROR_PHASE_UNSPECIFIED = 0;
  PRE_VALIDATION = 1;     // Input schema validation failed
  TRANSFORMATION = 2;     // utl-x execution failed
  POST_VALIDATION = 3;    // Output schema validation failed
  INTERNAL = 4;           // UTLXe engine error (not payload-related)
}
```

The `error_phase` is critical for debugging:
- `PRE_VALIDATION` → upstream produced invalid output
- `TRANSFORMATION` → the `.utlx` expression has a runtime error
- `POST_VALIDATION` → the mapping definition has a bug (produced invalid output)
- `INTERNAL` → UTLXe itself had a problem (OOM, thread exhaustion)

### 5.6 Error Classification

| Error | Phase | Classification | Wrapper action |
|---|---|---|---|
| Input schema violation | PRE_VALIDATION | PERMANENT | DLQ immediately |
| Input parse failure | PRE_VALIDATION | PERMANENT | DLQ immediately |
| Mapping expression error | TRANSFORMATION | Configurable | Per error_class config |
| Missing mapping input | TRANSFORMATION | PERMANENT | DLQ immediately |
| Type coercion failure | TRANSFORMATION | PERMANENT | DLQ immediately |
| Output schema violation | POST_VALIDATION | PERMANENT | DLQ immediately |
| UTLXe internal error | INTERNAL | TRANSIENT | Retry with backoff |
| Transformation timeout | TRANSFORMATION | TRANSIENT | Retry or DLQ after max_attempts |

### 5.7 Execution Flow with Validation and Error Phase

```kotlin
fun executeWithValidation(instance: TransformationInstance, request: ExecuteRequest): ExecuteResponse {
    val allWarnings = mutableListOf<ValidationError>()

    // PRE-VALIDATION
    if (instance.validateInput && instance.inputValidator != null) {
        val inputErrors = instance.inputValidator.validate(request.payload, request.contentType)
        if (inputErrors.isNotEmpty()) {
            when (instance.validationPolicy) {
                STRICT -> return ExecuteResponse(
                    success = false,
                    error_class = PERMANENT,
                    error_phase = PRE_VALIDATION,
                    validation_errors = inputErrors
                )
                WARN -> allWarnings.addAll(inputErrors)
                SKIP -> {}
            }
        }
    }

    // TRANSFORMATION (or pass-through if validate-only mode)
    val output = if (instance.strategy != null) {
        try {
            instance.strategy.execute(String(request.payload, UTF_8)).output
        } catch (e: Exception) {
            return ExecuteResponse(
                success = false,
                error_class = if (isTransient(e)) TRANSIENT else PERMANENT,
                error_phase = TRANSFORMATION,
                error = e.message
            )
        }
    } else {
        // Validate-only mode: pass through unchanged
        String(request.payload, UTF_8)
    }

    // POST-VALIDATION
    if (instance.validateOutput && instance.outputValidator != null) {
        val outputErrors = instance.outputValidator.validate(output.toByteArray(), ...)
        if (outputErrors.isNotEmpty()) {
            when (instance.validationPolicy) {
                STRICT -> return ExecuteResponse(
                    success = false,
                    error_class = PERMANENT,
                    error_phase = POST_VALIDATION,
                    validation_errors = outputErrors
                )
                WARN -> allWarnings.addAll(outputErrors)
                SKIP -> {}
            }
        }
    }

    return ExecuteResponse(
        success = true,
        output = output,
        validation_errors = allWarnings  // WARN-level errors (non-blocking)
    )
}
```

### 5.7 Validation Approach: Native Format-Level Validators

**Design decision:** Validation uses **native, format-specific validators** — NOT UDM comparison.

**Why NOT UDM-based validation:**
- UDM is a transformation intermediate — it normalizes data for processing, not for constraint enforcement
- UDM is lossy — it strips format-specific constraints (regex patterns, ranges, enumerations, facets, minLength, etc.)
- Comparing UDM trees checks structure ("field exists", "type matches") but NOT constraints ("value matches regex", "number within range", "string length < 50")
- A UDM comparison would give ~30% of what real schema validation provides

**Why native validation:**
- Purpose-built validation libraries understand ALL constraints per specification
- Standards-compliant: XSD per W3C spec, JSON Schema per draft-2020-12, Avro per Apache spec
- Field-level error messages: "field 'email' does not match pattern '^[a-z]+@.*$'" (not just "field is string")
- No intermediate UDM parse — validate raw payload bytes directly against compiled schema
- Faster — no UDM conversion step for validation

### 5.8 Supported Validators

| Data Format | Schema Format | Validation Library | Notes |
|-------------|--------------|-------------------|-------|
| JSON | JSCH (JSON Schema draft-07, 2020-12) | `com.networknt:json-schema-validator` | **New dependency** — full draft support with $ref resolution |
| YAML | JSCH (JSON Schema) | Same as JSON (YAML parsed to JSON first) | YAML → JSON → validate |
| XML | XSD (1.0, 1.1) | `javax.xml.validation` (JDK built-in) | No new dependency — JDK provides W3C-compliant XSD validation |
| CSV | TSCH (Frictionless Table Schema) | Custom (thin layer) | Validates column count, types, required fields, constraints |
| OData JSON | OSCH (OData/EDMX) | Custom (using parsed EDMX model) | Validates entity types, navigation properties, cardinality |
| Avro data | Avro Schema | `org.apache.avro.generic.GenericDatumReader` | Already in dependency tree — validates data against schema |
| Protobuf data | Proto descriptor | `com.google.protobuf.DynamicMessage.parseFrom` | Already in dependency tree — validates against descriptor |

**New dependency required:** Only `com.networknt:json-schema-validator:1.5.x` (~500KB, Apache 2.0 license). All others use JDK built-ins or libraries already in UTLXe's dependency tree.

### 5.9 Init-Time Compilation Per Format

Each validator is compiled at init time from the schema source into a reusable, thread-safe validator object:

```kotlin
interface SchemaValidator {
    /** Validate payload bytes against pre-compiled schema. Thread-safe, reusable. */
    fun validate(payload: ByteArray, contentType: String): List<ValidationError>
}

class SchemaValidatorFactory {
    fun create(schemaSource: String, schemaFormat: String): SchemaValidator {
        return when (schemaFormat) {
            "json-schema", "jsch" -> JsonSchemaValidator(schemaSource)   // networknt
            "xsd" -> XsdValidator(schemaSource)                          // javax.xml.validation
            "tsch" -> TableSchemaValidator(schemaSource)                 // custom
            "osch" -> ODataSchemaValidator(schemaSource)                 // custom
            "avro" -> AvroSchemaValidator(schemaSource)                  // apache avro
            "protobuf", "proto" -> ProtobufValidator(schemaSource)      // protobuf-java
            else -> throw IllegalArgumentException("Unsupported schema format: $schemaFormat")
        }
    }
}
```

**Init-time cost (one-time per transformation):**

| Schema format | Compilation cost | What happens |
|---|---|---|
| JSON Schema | ~10-50ms | Parse JSON, resolve $ref pointers, build validator tree |
| XSD | ~50-200ms | Parse XSD, build type system, create javax.xml.validation.Schema |
| TSCH | ~1-5ms | Parse JSON, extract column definitions |
| OSCH | ~10-50ms | Parse EDMX XML, build entity type model |
| Avro | ~5-20ms | Parse Avro JSON schema, build GenericData model |
| Protobuf | ~5-20ms | Parse .proto descriptor, build DynamicMessage descriptor |

**Runtime cost (per message):**

| Schema format | Validation cost | Payload size assumed |
|---|---|---|
| JSON Schema | ~50-200μs | 5KB JSON |
| XSD | ~100-500μs | 10KB XML |
| TSCH | ~10-50μs | 1KB CSV row |
| OSCH | ~50-200μs | 5KB OData JSON |
| Avro | ~20-100μs | 2KB Avro record |
| Protobuf | ~10-50μs | 1KB protobuf message |

These are comparable to transformation cost — validation is not a separate bottleneck.

---

## 6. CLI Interface (Main.kt changes)

### Current:
```
utlxe --bundle <path> [--config <path>] [--port <port>] [--validate]
```

### Target:
```
utlxe --bundle <path> [options]            # Bundle mode (standalone, backward compat)
utlxe --mode stdio-proto [options]         # Open-M integration (wrapper sends transforms via pipe)
utlxe --mode grpc --socket <path>          # gRPC mode on UDS (sidecar deployments)
utlxe --mode grpc --address <host:port>    # gRPC mode on TCP (external consumers)

Options:
  --bundle, -b <path>       Bundle directory (pre-load transforms from disk)
  --config, -c <path>       Engine config file
  --mode <mode>             Transport mode: stdio-json (default), stdio-proto, grpc
  --socket <path>           Unix socket path (gRPC mode)
  --address <host:port>     TCP address (gRPC mode)
  --port, -p <port>         Health endpoint port (default: 8081)
  --workers <n>             Worker thread pool size (default: CPU cores)
  --validate                Load and compile bundle, then exit (no processing)
  --version, -v             Show version
  --help, -h                Show help
```

### Mode behaviors and --bundle requirement:

| Mode | `--bundle` | How transforms are loaded | Init | Runtime |
|------|-----------|--------------------------|------|---------|
| `stdio-json` (default) | **REQUIRED** | From disk (bundle directory) at startup | Bundle → compile → READY | Read JSON lines from stdin, execute, write to stdout |
| `stdio-proto` | **OPTIONAL** | Dynamically via `LoadTransformationRequest` messages from the Go wrapper through the pipe. If `--bundle` also provided, those are pre-loaded at startup (hybrid). | Start transport → wait for Load messages → compile each → READY | Process `ExecuteRequest`, respond with `ExecuteResponse` |
| `grpc` | **OPTIONAL** | Dynamically via `LoadTransformation` RPC calls. If `--bundle` also provided, those are pre-loaded. | Start gRPC server → wait for Load RPCs → compile each → READY | Process `Execute` RPCs |

**Key distinction:**

- **`stdio-json` (standalone mode):** UTLXe owns its transformations. It reads `.utlx` files from a bundle directory on disk. This is for standalone use, CLI testing, and backward compatibility. The bundle must exist before UTLXe starts.

- **`stdio-proto` / `grpc` (integration mode):** The **caller owns the transformations**. The Go wrapper (or any client) sends the `.utlx` source code in `LoadTransformationRequest` messages at startup. UTLXe compiles and caches them. No disk access needed. This is for production deployment where the wrapper manages lifecycle and knows which mappings are needed from its pipeline descriptor.

**Hybrid mode:** If `--bundle` is provided in stdio-proto/grpc modes, bundle transforms are pre-loaded at startup (before any messages arrive). The caller can then load additional transforms dynamically. This supports scenarios where some transforms are "always needed" (from the bundle) and others are connection-specific (loaded dynamically by the wrapper).

### Startup sequence by mode:

**stdio-json (standalone):**
```
1. Parse --bundle path
2. BundleLoader discovers transforms from disk
3. Compile all transforms → cache Programs
4. Open stdin/stdout pipes
5. Health endpoint → READY
6. Process message loop
```

**stdio-proto (Open-M integration):**
```
1. Start reader/writer threads on stdin/stdout
2. Health endpoint → READY (no transforms loaded yet)
3. Wait for messages from wrapper:
   - LoadTransformationRequest → compile, cache Program, respond with success/failure
   - ExecuteRequest → look up cached Program, execute, respond
   - HealthRequest → respond with current state
   - UnloadTransformationRequest → remove from registry
4. Process continuously until pipe closes (pod shutdown)
```

**grpc (sidecar/external):**
```
1. Start gRPC server on UDS or TCP
2. Health endpoint → READY
3. Accept RPC calls:
   - LoadTransformation() → compile, cache, respond
   - Execute() → look up, execute, respond
   - Health() → respond
   - UnloadTransformation() → remove
4. Serve continuously until shutdown signal
```

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

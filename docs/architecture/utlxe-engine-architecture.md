# utlxe — Production Runtime Engine Architecture

**Document Version:** 1.0
**Last Updated:** 2026-04-02
**Status:** Design Proposal
**Target Release:** UTL-X v3.0

**Prerequisite Reading:**
- [Three-Phase Runtime Design (Template-Based)](three-phase-runtime-design.md)
- [Three-Phase Runtime Design (Validation-First / Copy-Based)](three-phase-runtime-validation-first.md)
- [CLI / Daemon Split Architecture](cli-daemon-split-architecture.md)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [The UTL-X Executable Trilogy](#2-the-utl-x-executable-trilogy)
3. [One Engine, Three Strategies](#3-one-engine-three-strategies)
4. [Multi-Transformation Support](#4-multi-transformation-support)
5. [I/O Pipe Model](#5-io-pipe-model)
6. [Thread Management](#6-thread-management)
7. [Memory Management](#7-memory-management)
8. [Engine Lifecycle](#8-engine-lifecycle)
9. [Monorepo Position](#9-monorepo-position)
10. [Configuration](#10-configuration)
11. [Security](#11-security)
12. [Deployment Formats](#12-deployment-formats)
13. [Comparison Table: utlx vs utlxd vs utlxe](#13-comparison-table-utlx-vs-utlxd-vs-utlxe)
14. [Implementation Roadmap](#14-implementation-roadmap)
15. [Open Questions](#15-open-questions)

---

## 1. Executive Summary

The two existing runtime design documents propose competing approaches to high-throughput message processing:

| Document | Approach | Runtime Model | Typical Latency |
|----------|----------|---------------|-----------------|
| [Template-Based](three-phase-runtime-design.md) | Generate templates from schema, fill at runtime | Lightweight, cloud-native | 7–20 ms/msg |
| [Validation-First / Copy-Based](three-phase-runtime-validation-first.md) | Build complete UDM, deep-copy at runtime | Enterprise middleware (Tibco BW inspired) | 8–25 ms/msg |

Both documents independently converge on the same `TransformExecutor` interface and the same three-phase lifecycle (design-time → init-time → runtime). They differ *only* in what happens during init-time preparation and the runtime execution loop. Everything else — lifecycle management, I/O, threading, monitoring, configuration — is identical.

**This document reconciles them** by defining **utlxe**: the production runtime engine. utlxe is a single long-running JVM process (with GraalVM native-image as a future option) that:

- Hosts **one or more transformations** (T1..Tn) from a project bundle
- Uses a **Strategy Pattern** for execution mode — template, copy, or compiled — selectable per transformation
- Manages **I/O pipes**, **thread pools**, **memory budgets**, and **health/metrics** as engine-level concerns
- Completes the UTL-X executable trilogy alongside `utlx` (CLI) and `utlxd` (daemon/IDE)

---

## 2. The UTL-X Executable Trilogy

```
┌─────────────────────────────────────────────────────────────────────┐
│                        UTL-X Executables                            │
├──────────────┬──────────────────┬───────────────────────────────────┤
│    utlx      │     utlxd        │           utlxe                   │
│  CLI tool    │  Daemon / IDE    │     Production Engine             │
│              │                  │                                    │
│  Single      │  LSP + REST      │  Multi-transformation             │
│  transform   │  Design-time     │  High-throughput                  │
│  Stateless   │  Long-running    │  Long-running                    │
│  CI/CD       │  Session-aware   │  Message processing              │
└──────────────┴──────────────────┴───────────────────────────────────┘
```

| Characteristic | `utlx` | `utlxd` | `utlxe` |
|----------------|--------|---------|---------|
| **Purpose** | Single transform execution | IDE integration, design-time | Production message processing |
| **Lifecycle** | Start → execute → exit | Long-running daemon | Long-running engine |
| **Concurrency** | Single-threaded | Multi-session | Multi-transformation, pooled threads |
| **Input** | Files, stdin | LSP messages, REST requests | Pipes (stdin, TCP, Kafka, HTTP) |
| **State** | Stateless | Session state | Transformation state + queues |
| **Deployment** | Native image, Docker | Fat JAR, Docker | Fat JAR, Docker, Kubernetes |
| **Use Case** | CI/CD, scripts, ad-hoc | Theia IDE, VS Code | ESB, Kafka consumer, API gateway |

### Tibco BusinessWorks Conceptual Mapping

For teams coming from enterprise middleware, the mapping is:

| Tibco BW Concept | UTL-X Equivalent |
|------------------|-------------------|
| BusinessWorks Designer | Theia IDE + `utlxd` |
| Process Definition (`.process`) — flow of activities | Flow (chain of transformations wired in `engine.yaml`) |
| Activity / Mapper — single step in a process | UTL-X transformation (`.utlx`) |
| EAR (deployment archive) | Project bundle (`.utlxp`) |
| BW Engine | `utlxe` |
| Engine Thread Pool | `utlxe` thread management |
| Process Starter (HTTP/Kafka/File) | Input pipe (transport-specific) |
| Schema (XSD) | USDL schema directives |
| Activity Palette | UTL-X stdlib functions |

---

## 3. One Engine, Three Strategies

### Key Decision

> **ONE engine (`utlxe`) with a configurable execution strategy per transformation, NOT two separate executables.**

**Reasoning:**

1. **80%+ of engine code is strategy-agnostic.** Lifecycle management, pipe I/O, thread pools, monitoring, configuration loading, health checks, metrics export — none of these change based on how a transformation executes.
2. **The `TransformExecutor` interface already exists in both design documents.** Both documents define the same interface with `transform(input)` and `transformBatch(inputs)`. This IS the strategy pattern — they just didn't name it that way.
3. **Two executables means double maintenance.** Two build targets, two Docker images, two sets of integration tests, two deployment guides, user confusion about which to pick.
4. **Strategy selection is a per-transformation concern, not a per-engine concern.** A single engine should be able to run T1 with template strategy and T2 with copy strategy simultaneously.

### The Three Strategies

```
┌─────────────────────────────────────────────────────────────────┐
│                    ExecutionStrategy                             │
│                    (Strategy Pattern)                            │
├─────────────────┬────────────────────┬──────────────────────────┤
│   TEMPLATE      │      COPY          │     COMPILED             │
│                 │                    │                          │
│  Init:          │  Init:             │  Init:                   │
│  Schema →       │  Schema →          │  AST →                   │
│  templates      │  full UDM +        │  JVM bytecode            │
│                 │  validators        │                          │
│  Runtime:       │  Runtime:          │  Runtime:                │
│  Fill template  │  Deep-copy →       │  Direct method           │
│  with data      │  fill → validate   │  invocation              │
└─────────────────┴────────────────────┴──────────────────────────┘
```

| Strategy | Init-Time | Runtime | Memory | Best For |
|----------|-----------|---------|--------|----------|
| **TEMPLATE** | Generate structure descriptors from schema | Allocate from template, fill with data | Lowest (just descriptors) | Cloud-native, high throughput, latency-sensitive |
| **COPY** | Build complete UDM model + pre-compile validators | Deep-copy pre-built model → fill → validate | Moderate (pre-built UDM + copy pool) | Enterprise middleware, validation-first, compliance |
| **COMPILED** | Compile transformation AST to JVM bytecode | Direct method invocation, no interpretation | Low but less predictable (JIT) | Maximum performance (Phase 2) |

### AUTO Mode

In addition to explicit strategy selection, utlxe supports **AUTO** mode where the engine selects the best strategy based on transformation characteristics:

```
AUTO selection logic:
  1. Schema available + validation required?     → COPY
  2. Schema available + no validation required?  → TEMPLATE
  3. No schema?                                  → TEMPLATE (fallback)
  4. Compiled strategy available + perf-critical? → COMPILED
```

### Strategy Interface

Both existing design documents define a `TransformExecutor` interface. utlxe formalizes this as the strategy contract:

```kotlin
/**
 * Strategy interface — the only point where template/copy/compiled differ.
 * Everything else in the engine is strategy-agnostic.
 */
interface ExecutionStrategy {
    /** One-time initialization (called during engine INITIALIZING state) */
    fun initialize(compiled: CompiledTransform, config: TransformConfig)

    /** Process a single message */
    fun execute(input: String): ExecutionResult

    /** Process a batch of messages (default: iterate over execute) */
    fun executeBatch(inputs: List<String>): List<ExecutionResult> =
        inputs.map { execute(it) }

    /** Release resources */
    fun shutdown()

    /** Strategy name for metrics/logging */
    val name: String
}

data class ExecutionResult(
    val output: String,
    val validationErrors: List<ValidationError> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)
```

```kotlin
class TemplateStrategy : ExecutionStrategy { /* from three-phase-runtime-design.md */ }
class CopyStrategy : ExecutionStrategy     { /* from three-phase-runtime-validation-first.md */ }
class CompiledStrategy : ExecutionStrategy { /* future: JVM bytecode generation */ }
class AutoStrategy : ExecutionStrategy     { /* delegates to one of the above */ }
```

---

## 4. Multi-Transformation Support

### Project Bundle

An engine loads a **project bundle** (`.utlxp`) containing one or more transformations. Each transformation is a **self-contained directory** with its own source, configuration, and schemas. The engine discovers transformations by scanning `transformations/*/transform.yaml`.

```
order-processing.utlxp/
├── engine.yaml
└── transformations/
    ├── xml-to-json/
    │   ├── xml-to-json.utlx
    │   ├── transform.yaml
    │   └── schemas/
    │       ├── order-v1.xsd          # input schema
    │       └── order-v1.json         # output schema
    ├── enrich-order/
    │   ├── enrich-order.utlx
    │   ├── transform.yaml
    │   └── schemas/
    │       ├── order-v1.json         # input schema
    │       ├── inventory-v1.json     # input schema
    │       └── invoice-v2.json       # output schema
    └── validate-output/
        ├── validate-output.utlx
        ├── transform.yaml
        └── schemas/
            └── invoice-v2.json       # input + output schema
```

**Design rationale:**
- **Add a transformation** = drop a new directory under `transformations/`
- **Remove a transformation** = delete the directory
- **Each transformation is fully portable** — move or copy the folder and nothing else breaks
- **No central manifest** — the engine discovers what's available by scanning the directory structure
- **Schemas live with their transformation** — even if the same schema appears in multiple directories, each transformation is self-contained. Schema drift between copies is a tooling concern (the engine can detect and warn).

### Per-Transformation Configuration (transform.yaml)

Each transformation directory contains a `transform.yaml` that declares its strategy, inputs, output, and resource limits:

```yaml
# transformations/xml-to-json/transform.yaml
strategy: TEMPLATE
inputs:
  - name: order
    schema: schemas/order-v1.xsd
output:
  schema: schemas/order-v1.json
maxConcurrent: 8
```

```yaml
# transformations/enrich-order/transform.yaml
strategy: COPY
validationPolicy: WARN     # STRICT | WARN | SKIP
inputs:
  - name: order
    schema: schemas/order-v1.json
  - name: inventory
    schema: schemas/inventory-v1.json
output:
  schema: schemas/invoice-v2.json
maxConcurrent: 4
```

```yaml
# transformations/validate-output/transform.yaml
strategy: AUTO
inputs:
  - name: invoice
    schema: schemas/invoice-v2.json
output:
  schema: schemas/invoice-v2.json
```

### Transformation Registry

```kotlin
class TransformationRegistry {
    private val transformations: Map<String, TransformationInstance>

    fun register(name: String, instance: TransformationInstance)
    fun get(name: String): TransformationInstance?
    fun list(): List<TransformationInstance>
    fun remove(name: String)  // for hot reload
}

class TransformationInstance(
    val name: String,
    val compiled: CompiledTransform,
    val strategy: ExecutionStrategy,
    val config: TransformConfig,
    val metrics: TransformMetrics,
    val memoryPool: ObjectPool?
)
```

### Chained Transformations (Flows)

Transformations can be chained so that the output of T1 feeds into the input of T2 via in-process pipes:

```
T1 (xml-to-json)  ──output──→  in-process queue  ──input──→  T2 (enrich-order)
                                                                    │
                                                              ──input──→  inventory (Kafka pipe)
```

Chaining is declared in `engine.yaml` via pipe wiring and optional named flows — not in the individual `transform.yaml` files. Each transformation is self-contained and reusable; the engine wires them together. This mirrors the Tibco BW model where a Process (flow) wires together Activities (transformations). See [Section 10](#10-configuration) for the `pipes:` and `flows:` configuration.

---

## 5. I/O Pipe Model

### Key Decision

> **Multiple separate input pipes (one per input slot) with correlation, NOT a single multipart pipe.**

**Reasoning:**

1. **Maps to real-world systems.** One Kafka topic per input, one HTTP endpoint per input, one file per input. No custom framing protocol needed.
2. **Each pipe is independently readable/writable.** A pipe can be backed by any transport without affecting other pipes.
3. **Correlation via `correlationId`.** When a transformation has multiple inputs, related messages across pipes share a `correlationId` header.
4. **A multipart pipe would need custom framing** (length-prefixed chunks, content-type markers) with no existing tooling support.

### Pipe Architecture

```
                    ┌─────────────────────────────────────┐
                    │         TransformationInstance       │
                    │                                     │
  Input Pipe A ───→ │  ┌─────────┐   ┌────────────────┐  │
  (Kafka topic)     │  │ Correlator│→ │ ExecutionStrategy│ │ ──→ Output Pipe
                    │  │         │   │                │  │     (stdout / Kafka / TCP)
  Input Pipe B ───→ │  └─────────┘   └────────────────┘  │
  (HTTP endpoint)   │                                     │
                    └─────────────────────────────────────┘
```

### Pipe Transports

| Transport | Direction | Use Case |
|-----------|-----------|----------|
| **stdin / stdout** | In / Out | CLI integration, Unix pipelines |
| **In-process queue** | In / Out | Chained transformations (zero-copy when format matches) |
| **TCP socket** | In / Out | Remote network integration |
| **Kafka consumer / producer** | In / Out | Message broker integration |
| **HTTP endpoint** | In | REST API input (engine hosts endpoint) |

### Correlation

When a transformation declares multiple inputs, incoming messages must be correlated:

```
Input pipe "order":     { correlationId: "abc-123", data: "<Order>...</Order>" }
Input pipe "inventory": { correlationId: "abc-123", data: "{\"sku\": ...}" }
```

The engine's **Correlator** buffers messages until all inputs for a given `correlationId` are available, then dispatches to the strategy.

**Single-input shorthand:** When a transformation has only one input, no correlation is needed — messages flow directly to the strategy.

### Pipe Interface

```kotlin
interface InputPipe {
    val name: String
    fun read(): Message          // blocking read
    fun tryRead(): Message?      // non-blocking
    fun close()
}

interface OutputPipe {
    val name: String
    fun write(message: Message)
    fun close()
}

data class Message(
    val correlationId: String?,
    val payload: ByteArray,
    val contentType: String,      // "application/xml", "application/json", etc.
    val headers: Map<String, String> = emptyMap()
)
```

---

## 6. Thread Management

### Key Decision

> **Shared thread pool by default, with option for per-transformation dedicated threads.**

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                          utlxe                                │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐    │
│  │            Shared ForkJoinPool (default)               │    │
│  │            work-stealing, configurable size            │    │
│  │                                                        │    │
│  │  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐   │    │
│  │  │ W-1  │  │ W-2  │  │ W-3  │  │ W-4  │  │ W-N  │   │    │
│  │  └──────┘  └──────┘  └──────┘  └──────┘  └──────┘   │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌──────────────────────────────────┐                        │
│  │  Dedicated Pool (T2: enrich)     │  ← isolation mode      │
│  │  ┌──────┐  ┌──────┐             │                        │
│  │  │ W-A  │  │ W-B  │             │                        │
│  │  └──────┘  └──────┘             │                        │
│  └──────────────────────────────────┘                        │
└──────────────────────────────────────────────────────────────┘
```

### Configuration Per Transformation

| Parameter | Default | Description |
|-----------|---------|-------------|
| `workerThreads` | shared pool | Number of dedicated threads (0 = use shared pool) |
| `queueCapacity` | 1024 | Max messages queued before back-pressure |
| `maxConcurrent` | CPU cores | Max concurrent executions |

### Back-Pressure

When a transformation's queue reaches `queueCapacity`:

1. **Stop reading** from input pipes for that transformation
2. **Signal upstream** (Kafka: pause consumer, HTTP: return 503, TCP: stop accepting)
3. **Resume** when queue drops below 75% capacity

This prevents unbounded memory growth and cascading failures.

### Intra-Transformation Parallelism

UTL-X transformations are **purely functional** (no side effects, no mutable state). This enables an optional optimization: parallelize `map` and `filter` operations within a single message transformation.

```yaml
# Per-transformation config
parallelizeOperations: true   # default: false
minParallelElements: 100      # only parallelize collections with 100+ elements
```

This is safe *because* UTL-X is purely functional — there are no data races by design.

---

## 7. Memory Management

### Per-Transformation Memory Budgets

Each transformation gets its own memory budget and object pools. Pools are **not shared** between transformations because different transformations produce different UDM shapes.

```
┌─────────────────────────────────────────────┐
│  T1: xml-to-json (TEMPLATE strategy)        │
│  Budget: 256 MB                             │
│  Pool: structure descriptors                │
│  Footprint: ~2 KB per concurrent message    │
├─────────────────────────────────────────────┤
│  T2: enrich-order (COPY strategy)           │
│  Budget: 512 MB                             │
│  Pool: pre-built UDM copies                 │
│  Footprint: ~50 KB per concurrent message   │
├─────────────────────────────────────────────┤
│  T3: compiled-fast (COMPILED strategy)      │
│  Budget: 128 MB                             │
│  Pool: minimal (JIT-managed)                │
│  Footprint: varies                          │
└─────────────────────────────────────────────┘
```

### Strategy Memory Profiles

| Strategy | Init-Time Allocation | Per-Message Allocation | GC Pressure |
|----------|---------------------|----------------------|-------------|
| **TEMPLATE** | Structure descriptors only | Allocate from template, fill, return to pool | Lowest |
| **COPY** | Full UDM model + validator instances | Deep-copy from pool, fill, validate, return | Moderate |
| **COMPILED** | Bytecode + JIT compilation buffers | Direct allocation (JIT-optimized) | Low but less predictable |

---

## 8. Engine Lifecycle

### State Machine

```
  ┌─────────┐     ┌──────────────┐     ┌───────┐     ┌─────────┐
  │ CREATED │────→│ INITIALIZING │────→│ READY │────→│ RUNNING │
  └─────────┘     └──────────────┘     └───────┘     └────┬────┘
                                            ↑              │
                                            │         ┌────▼────┐
                                            │         │DRAINING │
                                            │         └────┬────┘
                                            │              │
                                       (hot reload)   ┌────▼────┐
                                                      │ STOPPED │
                                                      └─────────┘
```

| State | Description |
|-------|-------------|
| **CREATED** | Engine instantiated, no transformations loaded |
| **INITIALIZING** | Loading project bundle, compiling transformations, initializing strategies |
| **READY** | All transformations initialized, pipes not yet connected |
| **RUNNING** | Processing messages |
| **DRAINING** | Shutdown requested; finish in-flight messages, stop accepting new ones |
| **STOPPED** | All resources released |

### Hot Reload

The engine supports adding, removing, and updating individual transformations without a full restart:

1. New transformation compiled and initialized in background
2. Old instance enters DRAINING (finishes in-flight messages)
3. New instance swapped in atomically
4. Old instance resources released

This is critical for zero-downtime deployments in Kubernetes.

### Health & Metrics

**Health endpoint** (HTTP `/health`):

```json
{
  "status": "UP",
  "engine": {
    "state": "RUNNING",
    "uptime": "2h 34m",
    "transformations": 3
  },
  "transformations": {
    "xml-to-json": { "status": "RUNNING", "queueDepth": 12 },
    "enrich-order": { "status": "RUNNING", "queueDepth": 0 },
    "validate-output": { "status": "RUNNING", "queueDepth": 3 }
  }
}
```

- **Liveness probe:** `/health/live` — engine process is alive
- **Readiness probe:** `/health/ready` — engine is in RUNNING state, all transformations healthy

**Metrics** (JMX + optional Prometheus `/metrics`):

Per-transformation:
- `utlxe_messages_processed_total{transform="xml-to-json"}`
- `utlxe_message_latency_seconds{transform="xml-to-json", quantile="0.99"}`
- `utlxe_queue_depth{transform="xml-to-json"}`
- `utlxe_errors_total{transform="xml-to-json"}`
- `utlxe_validation_failures_total{transform="enrich-order"}`

Engine-level:
- `utlxe_active_threads`
- `utlxe_heap_used_bytes`
- `utlxe_uptime_seconds`

---

## 9. Monorepo Position

### New Module

Add `modules/engine/` as a single Gradle module. Start simple — extract sub-modules later only if the module grows beyond a manageable size.

```
utl-x/
├── modules/
│   ├── core/          ← language model, compiler, UDM
│   ├── cli/           ← utlx CLI
│   ├── daemon/        ← utlxd (LSP + REST)
│   ├── analysis/      ← static analysis, type checking
│   └── engine/        ← utlxe (NEW)
│       ├── build.gradle.kts
│       └── src/main/kotlin/org/utlx/engine/
│           ├── UtlxEngine.kt              ← engine shell, lifecycle
│           ├── strategy/
│           │   ├── ExecutionStrategy.kt    ← strategy interface
│           │   ├── TemplateStrategy.kt
│           │   ├── CopyStrategy.kt
│           │   └── AutoStrategy.kt
│           ├── pipe/
│           │   ├── InputPipe.kt
│           │   ├── OutputPipe.kt
│           │   ├── StdioPipe.kt
│           │   ├── InProcessPipe.kt
│           │   └── Correlator.kt
│           ├── registry/
│           │   └── TransformationRegistry.kt
│           ├── threading/
│           │   └── ThreadPoolManager.kt
│           ├── health/
│           │   └── HealthEndpoint.kt
│           └── config/
│               └── EngineConfig.kt
├── formats/           ← xml, json, csv, yaml, xsd, ...
├── stdlib/
├── schema/            ← USDL
└── settings.gradle.kts
```

### Gradle Dependencies

```kotlin
// modules/engine/build.gradle.kts
dependencies {
    implementation(project(":modules:core"))
    implementation(project(":modules:analysis"))
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:yaml"))
    implementation(project(":formats:xsd"))
    implementation(project(":formats:jsch"))
    implementation(project(":formats:avro"))
    implementation(project(":formats:protobuf"))
    implementation(project(":stdlib"))
    implementation(project(":schema"))
}
```

### settings.gradle.kts Addition

```kotlin
include("modules:engine")   // utlxe — production runtime engine
```

### Daemon Embedding

The daemon (`utlxd`) can optionally embed an engine instance for IDE preview and test-run features. This is a compile-time dependency, not a runtime requirement:

```kotlin
// modules/daemon/build.gradle.kts
dependencies {
    implementation(project(":modules:engine"))  // optional: for preview/test-run
}
```

---

## 10. Configuration

Configuration is split between engine-level concerns (`engine.yaml`) and per-transformation concerns (`transform.yaml` in each transformation directory).

### engine.yaml (engine-level)

The single `engine.yaml` at the bundle root handles engine-wide settings: threads, monitoring, default strategy, and **flow wiring** (how transformations chain together).

```yaml
engine:
  name: order-processing-engine

  # Thread management
  threads:
    sharedPoolSize: 16          # 0 = CPU cores

  # Memory
  memory:
    maxHeap: 2g                 # JVM -Xmx equivalent hint

  # Monitoring
  monitoring:
    health:
      port: 8081
      path: /health
    metrics:
      enabled: true
      prometheus:
        port: 9090
        path: /metrics
    jmx:
      enabled: true

  # Default strategy (can be overridden per transform.yaml)
  defaultStrategy: AUTO

# Pipe wiring — connects transformations to external transports and to each other.
# Per-transformation config (strategy, schemas, maxConcurrent) lives in each
# transformation's own transform.yaml. This section only declares I/O plumbing.
pipes:
  xml-to-json:
    inputs:
      - name: order
        transport: kafka
        topic: orders-xml
        group: utlxe-order-processing
    output:
      transport: kafka
      topic: orders-json

  enrich-order:
    inputs:
      - name: order
        transport: in-process     # chained from xml-to-json output
        source: xml-to-json
      - name: inventory
        transport: kafka
        topic: inventory-updates
        group: utlxe-order-processing
    output:
      transport: stdout

  validate-output:
    inputs:
      - name: invoice
        transport: in-process
        source: enrich-order
    output:
      transport: kafka
      topic: invoices-validated

# Named flows — optional, for documentation and operational clarity.
# The engine infers the flow graph from pipe wiring above. Declaring a flow
# gives it a name for health/metrics and validates the chain at startup.
flows:
  order-to-invoice:
    steps: [xml-to-json, enrich-order, validate-output]
```

### transform.yaml (per-transformation)

Each transformation's `transform.yaml` declares what the transformation *is* — its strategy, schemas, and resource limits. See [Section 4](#4-multi-transformation-support) for examples.

### Configuration Precedence

1. **Command-line flags** (highest priority)
2. **Environment variables** (`UTLXE_THREADS_SHARED_POOL_SIZE=16`)
3. **engine.yaml** + per-transformation `transform.yaml`
4. **Defaults** (lowest priority)

---

## 11. Security

### Input Validation

- **COPY strategy:** Validation is built-in (validation-first by design, per [validation-first doc](three-phase-runtime-validation-first.md))
- **TEMPLATE strategy:** Optional validation as a pre-step (configurable via `validationPolicy`)
- **Validation policy per transformation:** `STRICT` (reject invalid), `WARN` (log and continue), `SKIP` (no validation)

### Transport Security

| Transport | Security |
|-----------|----------|
| TCP socket | TLS required for non-localhost connections |
| Kafka | Inherits Kafka security config (SASL, SSL) |
| HTTP endpoint | TLS optional, configurable |
| stdin/stdout | OS-level process isolation |
| In-process | No transport — same JVM |

### Resource Isolation

- Per-transformation memory budgets (enforced via pool limits)
- Per-transformation queue capacity limits
- Per-transformation concurrency limits
- Engine-level max heap

### Functional Purity

UTL-X transformations are **purely functional** — no file I/O, no network calls, no mutable shared state within a transformation. This provides strong security guarantees:

- A transformation cannot exfiltrate data via side channels
- A transformation cannot modify engine state
- A transformation cannot affect other transformations

---

## 12. Deployment Formats

| Format | Use Case | Notes |
|--------|----------|-------|
| **Fat JAR** | Traditional JVM deployment | `java -jar utlxe.jar --bundle order-processing.utlxp` |
| **Docker image** | Containerized deployment | Base: Eclipse Temurin JRE 21 |
| **GraalVM native image** | Fast startup, low memory | Phase 4; requires reflection config |
| **Kubernetes Helm chart** | Orchestrated deployment | Health probes, HPA, ConfigMaps |

### Docker Example

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY utlxe.jar /app/utlxe.jar
COPY order-processing.utlxp /app/bundle.utlxp
EXPOSE 8081 9090
ENTRYPOINT ["java", "-jar", "/app/utlxe.jar", "--bundle", "/app/bundle.utlxp"]
```

### Kubernetes Example

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: utlxe-order-processing
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: utlxe
          image: utlx/utlxe:latest
          ports:
            - containerPort: 8081  # health
            - containerPort: 9090  # metrics
          livenessProbe:
            httpGet:
              path: /health/live
              port: 8081
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8081
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "2000m"
```

---

## 13. Comparison Table: utlx vs utlxd vs utlxe

| Aspect | `utlx` (CLI) | `utlxd` (Daemon) | `utlxe` (Engine) |
|--------|--------------|-------------------|-------------------|
| **Transforms** | 1 | 1 (per request) | 1..N (concurrent) |
| **Lifecycle** | Ephemeral | Long-running | Long-running |
| **Strategy** | Simple (no strategy) | Simple (no strategy) | Template / Copy / Compiled / Auto |
| **I/O** | Files, stdin/stdout | LSP, REST | Pipes (multi-transport) |
| **Threading** | Single | Multi-session | Pooled, configurable |
| **Memory** | Allocate-and-exit | Session-scoped | Pooled, budgeted |
| **Monitoring** | Exit code | LSP diagnostics | Health, metrics, JMX |
| **Hot Reload** | N/A | File watcher | Per-transformation reload |
| **Deployment** | Native image | Fat JAR | Fat JAR, Docker, K8s, native |
| **Config** | CLI flags | daemon.yaml | engine.yaml + per-transformation transform.yaml |
| **Typical Latency** | N/A (batch) | N/A (interactive) | 5–25 ms/msg (strategy-dependent) |

---

## 14. Implementation Roadmap

### Phase 1 — Engine Shell + Template Strategy + stdio Pipes

- Engine lifecycle state machine (CREATED → STOPPED)
- `modules/engine` Gradle module with dependencies
- Template strategy (from [three-phase-runtime-design.md](three-phase-runtime-design.md))
- stdin/stdout pipes (single input, single output)
- Single-transformation loading
- Basic health endpoint

**Exit criteria:** `utlxe --bundle simple.utlxp` processes messages from stdin to stdout using template strategy.

### Phase 2 — Copy Strategy + Multi-Transformation + In-Process Pipes

- Copy strategy (from [three-phase-runtime-validation-first.md](three-phase-runtime-validation-first.md))
- AUTO strategy (selects template or copy)
- Multi-transformation registry
- In-process pipes for chaining
- Correlator for multi-input transformations
- Project bundle format (directory scanning, transform.yaml discovery)

**Exit criteria:** Engine loads a `.utlxp` bundle with two chained transformations, one using template and one using copy strategy.

### Phase 3 — Thread Pools, Back-Pressure, Monitoring

- Shared ForkJoinPool with work-stealing
- Dedicated per-transformation thread pools
- Back-pressure (queue capacity → stop reading)
- Prometheus metrics endpoint
- JMX beans
- Hot reload of individual transformations
- Kafka and TCP pipe transports

**Exit criteria:** Engine runs in production with Kafka input, multiple transformations, monitoring dashboards.

### Phase 4 — Compiled Strategy + GraalVM Native

- Compiled strategy (AST → JVM bytecode)
- GraalVM native-image support
- Performance benchmarks (template vs copy vs compiled)
- Helm chart for Kubernetes deployment

**Exit criteria:** All three strategies benchmarked; GraalVM native image starts in <100ms.

---

## 15. Open Questions

| # | Question | Options | Impact |
|---|----------|---------|--------|
| 1 | **Project bundle packaging** | Deploy as directory on disk, or also support ZIP archive for transport? | Deployment, tooling |
| 2 | **Pipe framing protocol** | Length-prefixed binary vs newline-delimited JSON? | stdin/TCP transports |
| 3 | **Schema registry integration** | Confluent Schema Registry for Kafka schemas? | Kafka transport |
| 4 | **Clustering / multi-instance** | Engine-level coordination or delegate to Kubernetes? | Horizontal scaling |
| 5 | **Management REST API** | Admin endpoints for runtime inspection and control? | Operations |
| 6 | **Compiled strategy scope** | Full JVM bytecode or limit to expression compilation? | Phase 4 complexity |
| 7 | **Daemon embedding** | Full engine embed in `utlxd` or lightweight preview-only? | IDE experience |

---

*This document supersedes neither [three-phase-runtime-design.md](three-phase-runtime-design.md) nor [three-phase-runtime-validation-first.md](three-phase-runtime-validation-first.md). It reconciles both under a unified engine architecture. The strategy implementations in Phase 1 and Phase 2 should follow the detailed designs in those respective documents.*

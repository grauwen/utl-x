# EF19: HealthResponse Enrichment & Engine Introspection

**Status:** Partially Accepted (see assessment below)
**Priority:** Low (version field), Medium (introspection RPCs), Medium (Dapr health)
**Created:** May 2026
**Origin:** Open-M change request [UTLX-CH-001](https://github.com/open-m/docs/changes/utlx/UTLX-CH-001-health-response-enrichment.md)

---

## Change Request Summary

Open-M's wrapper runs UTLXe as a subprocess. The `omctl inspect instance` command wants to display UTLXe runtime state. The current `HealthResponse` (5 fields) is too thin for what Open-M's `UtlxeSubprocessInfo` declares. The request asks UTL-X to add 7 new fields to `HealthResponse`:

| Requested Field | Source |
|----------------|--------|
| `version` | Build-time constant |
| `workers` | Executor config |
| `jvm_heap_used_bytes` | `Runtime.getRuntime()` |
| `jvm_heap_max_bytes` | `Runtime.getRuntime()` |
| `avg_execution_duration_us` | Rolling average |
| `p99_execution_duration_us` | Histogram |
| `max_execution_duration_us` | Atomic max |

## Critical Assessment

### What's already covered elsewhere

**JVM heap metrics are already available via OpenTelemetry (EF14).** The Azure Monitor Java agent auto-instruments JVM heap, GC, and thread metrics. Any monitoring dashboard (Application Insights, Grafana) already shows heap usage in real-time with history, alerting, and trends. Duplicating this into the health endpoint provides a single snapshot with no history — strictly less useful than what OTel already exports.

**Heap usage is already tracked internally (EF15).** The backpressure system reads `Runtime.totalMemory/freeMemory/maxMemory` every 100ms. The data exists in-process. But exposing it via the health RPC is a different question from whether it should exist.

### Field-by-field verdict

| Field | Verdict | Reasoning |
|-------|---------|-----------|
| `version` | **Accept** | Cheap, static, genuinely useful for compatibility detection. One string field, zero runtime cost. |
| `thread_pool_size` | **Accept** (as `workers`) | Accepted but renamed from `thread_pool_size` to `workers` for consistency with Azure scaling terminology. Static configuration value with zero runtime cost. While the wrapper configures `sharedPoolSize` at startup, it may not retain the effective value (e.g., `0` means "CPU cores" — only UTLXe knows the resolved number, e.g., `8`). Exposed as `HealthResponse.workers` (field 7). |
| `jvm_heap_used_bytes` | **Reject** (superseded) | Raw bytes are a stale snapshot. Replaced by `HealthResponse.heap_usage_percent` (field 8) — a pre-computed percentage from EF15's backpressure background thread, updated every 100ms. Answers "how close am I to rejecting?" rather than "how many bytes?" |
| `jvm_heap_max_bytes` | **Reject** (superseded) | Static JVM setting the wrapper configured via `-Xmx`. Superseded by `heap_backpressure_threshold` (field 9) which, together with `heap_usage_percent`, tells the wrapper everything it needs: "at what percentage do I reject, and where am I now?" Raw max bytes adds no information beyond what the wrapper already knows. |
| `avg_execution_duration_us` | **Reject** | Requires per-execution tracking on the hot path. The change request claims "atomics — negligible overhead" but a rolling average requires either a ring buffer (memory) or exponential decay (computation) on every single execution. ExecuteMetrics already reports per-execution duration. If Open-M needs an aggregate, it should compute it on the wrapper side from the per-execution values it already receives. Pushing aggregation into the engine adds complexity and hot-path cost for one consumer's convenience. |
| `p99_execution_duration_us` | **Reject** | Requires a histogram data structure (HdrHistogram: ~16-32 KB per instance) maintained on every execution. This is observability infrastructure — it belongs in OpenTelemetry, not in a health check. EF14 already exports duration as a custom span attribute; proper percentile computation happens in the backend (Application Insights, Prometheus). |
| `max_execution_duration_us` | **Reject** | Mildly useful but misleading — a single outlier from startup or cold-start will dominate forever. Without reset semantics or windowing, this number becomes useless quickly. If needed, the wrapper can track `max(response.metrics.execute_duration_us)` itself. |

### Performance concerns

The change request downplays hot-path impact:

> "The duration tracking needs to be in the hot path (per-execution) but uses atomics — negligible overhead."

This is misleading. A `CAS` loop on `AtomicLong` under contention (parallel transforms) causes cache-line bouncing across CPU cores. For `avg` you need two atomics (sum + count) or a lock-free ring buffer. For `p99` you need a histogram. All of this executes on **every single message** to serve a health check that runs every few seconds. The cost-benefit ratio is wrong.

UTLXe's hot path is currently clean: parse, transform, serialize, respond. No shared mutable state except the backpressure `volatile double` (which is write-once-per-100ms from a background thread, not per-execution). Adding per-execution atomic updates introduces contention that degrades throughput under load — exactly when performance matters most.

### Architectural concern: health check vs metrics

The change request conflates two different concerns:

1. **Health** — "is the engine alive and ready?" → boolean-ish, checked every few seconds
2. **Metrics** — "how is the engine performing?" → time-series, sampled continuously

`HealthResponse` is the right place for (1). It is the wrong place for (2). Adding `avg_duration`, `p99_duration`, `heap_used` turns a health check into a poor man's metrics endpoint. OpenTelemetry (EF14) is already the proper metrics pipeline.

### What Open-M should do instead

For the fields being rejected, the wrapper already has the data or can compute it:

| Open-M field | Where to get it |
|-------------|-----------------|
| `thread_pool_size` / `workers` | Now in `HealthResponse.workers` (field 7) — renamed from `thread_pool_size` to `workers` for Azure consistency. Reports the resolved value (e.g., `8`), not the config input (e.g., `0`). |
| `jvm_heap_max_bytes` | Superseded by `heap_usage_percent` + `heap_backpressure_threshold`. Raw max bytes is a config value the wrapper already set via `-Xmx`. |
| `jvm_heap_used_bytes` | Replaced by `HealthResponse.heap_usage_percent` (field 8) — a pre-computed percentage from EF15, not raw bytes |
| `avg_transform_duration_us` | Compute from `ExecuteResponse.metrics.execute_duration_us` values the wrapper already receives |
| `transforms_total` | Already in `HealthResponse.total_executions` |
| `transform_errors_total` | Already in `HealthResponse.total_errors` |

The wrapper processes every `ExecuteResponse`. It can maintain its own running average, max, and count without any UTLXe changes.

---

## Accepted Changes

EF19 delivers three things: a `version` field on `HealthResponse`, introspection RPCs for querying loaded state, and Dapr-compatible health probing.

### Part 1: HealthResponse Enrichment

Add **five fields** to `HealthResponse`:

```protobuf
message HealthResponse {
  string state = 1;                  // "READY", "RUNNING", "DRAINING", etc.
  int64 uptime_ms = 2;
  int32 loaded_transformations = 3;
  int64 total_executions = 4;
  int64 total_errors = 5;

  // EF19: version and runtime config
  string version = 6;               // UTLXe build version (e.g., "1.1.0")
  int32 workers = 7;                // Resolved number of concurrent transformation workers

  // EF19: backpressure status (from EF15 — already computed, zero additional cost)
  int32 heap_usage_percent = 8;     // Current heap usage (0-100), cached by background thread
  int32 heap_backpressure_threshold = 9; // Configured threshold (0-100), e.g., 85
  bool heap_pressure = 10;          // True when actively rejecting messages due to memory pressure
}
```

**Why these fields:**

| Field | Cost | Reasoning |
|-------|------|-----------|
| `version` | Zero (static) | Not available elsewhere. Enables compatibility detection. |
| `workers` | Zero (static) | Only UTLXe knows the resolved value (config `0` → actual `8`). Essential for capacity planning. Renamed from CH-001's `thread_pool_size` for Azure consistency. |
| `heap_usage_percent` | Zero (read cached volatile) | Already computed every 100ms by EF15 background thread. Reading a cached number is ~1 nanosecond. This is an **operational health signal**: "how close am I to rejecting messages?" |
| `heap_backpressure_threshold` | Zero (read config) | Needed to interpret `heap_usage_percent`. Without the threshold, the wrapper can't tell if 70% is fine (threshold=85) or critical (threshold=75). |
| `heap_pressure` | Zero (read cached volatile) | The key health signal: "am I actively rejecting messages right now?" This is a **health** question, not a metrics question. The wrapper needs this to show status in `omctl inspect`. |

**Why this is different from the rejected `jvm_heap_used_bytes` / `jvm_heap_max_bytes`:**

The original CH-001 requested raw JVM memory bytes. Those were rejected because they're stale snapshots better served by OTel time-series. The backpressure fields are fundamentally different:
- They are **already computed** (EF15) — no new instrumentation
- They answer **health questions** ("are you rejecting?"), not metrics questions ("how many bytes?")
- The `heap_pressure` boolean is the single most important operational signal for the wrapper — it determines whether Dapr should route messages to this instance
- Reading them is a volatile read (~1ns), not a JVM runtime call

**Implementation:**

```kotlin
// In TransportHandlers.kt
fun handleHealth(engine: UtlxEngine): HealthResponse {
    val registry = engine.registry
    val totalExecutions = registry.list().sumOf { it.executionCount.get() }
    val totalErrors = registry.list().sumOf { it.errorCount.get() }

    return HealthResponse.newBuilder()
        .setState(engine.state.name)
        .setUptimeMs(engine.uptimeMs())
        .setLoadedTransformations(registry.size())
        .setTotalExecutions(totalExecutions)
        .setTotalErrors(totalErrors)
        .setVersion(BuildInfo.VERSION)  // NEW — compile-time constant from Gradle
        .setWorkers(engine.effectiveWorkers)  // NEW — resolved value (e.g., 8, not 0)
        .setHeapUsagePercent(engine.heapUsagePercent().toInt())  // NEW — cached by EF15 background thread
        .setHeapBackpressureThreshold((engine.heapBackpressureThreshold * 100).toInt())  // NEW — config value
        .setHeapPressure(engine.isHeapPressure())  // NEW — actively rejecting?
        .build()
}
```

Gradle injects the version:

```kotlin
// build.gradle.kts
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/source/buildinfo")
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("""
            package org.apache.utlx.engine
            object BuildInfo {
                const val VERSION = "${project.version}"
            }
        """.trimIndent())
    }
}
```

---

### Part 2: Introspection RPCs

The real gap in the current proto is not the health response — it's the complete absence of **read-only introspection**. Currently, once a transformation or bundle is loaded, there is no way to query what's loaded. The proto only has mutating operations (Load/Unload/Execute) and a minimal health check.

This matters for:
- **Wrapper restarts** — the wrapper loses its in-memory state and needs to rediscover what UTLXe has loaded
- **Debugging** — "which transformations are active? which bundle loaded them?"
- **`omctl inspect`** — Open-M's original use case
- **Operational tooling** — any admin dashboard or CLI

#### Current introspection gap

| Question | Proto answer today |
|----------|-------------------|
| Is the engine alive? | `Health()` — yes |
| What version is it? | **Nothing** (fixed by Part 1) |
| What transformations are loaded? | **Nothing** — only known at `LoadTransformation` time |
| What bundles are loaded? | **Nothing** — only known at `LoadBundle` time |
| What's the config of a transformation? | **Nothing** |
| What strategy is a transformation using? | **Nothing** |

#### Proposed proto additions

```protobuf
// ─── Introspection messages (read-only, no hot-path impact) ───

message ListTransformationsRequest {}

message ListTransformationsResponse {
  repeated TransformationInfo transformations = 1;
}

message TransformationInfo {
  string transformation_id = 1;     // The ID used in Load/Execute
  string strategy = 2;              // "TEMPLATE", "COPY", "COMPILED"
  int64 loaded_at_ms = 3;          // Unix timestamp when loaded
  int64 execution_count = 4;       // Total executions for this transformation
  int64 error_count = 5;           // Total errors for this transformation
  string bundle_id = 6;            // Which bundle loaded this (empty if loaded individually)
  int32 max_concurrent = 7;        // Per-transformation concurrency limit (0 = engine default)
  string input_format = 8;         // Detected/configured input format ("json", "xml", etc.)
  string output_format = 9;        // Detected/configured output format
}

message ListBundlesRequest {}

message ListBundlesResponse {
  repeated BundleInfo bundles = 1;
}

message BundleInfo {
  string bundle_id = 1;             // Bundle identifier
  string version = 2;               // Bundle version (from engine.yaml)
  string checksum = 3;              // SHA-256 of the bundle
  int64 loaded_at_ms = 4;          // Unix timestamp when loaded
  int32 transformation_count = 5;  // Number of transformations in this bundle
  repeated string transformation_ids = 6; // Names of transformations in this bundle
}
```

#### Service definition additions

```protobuf
service UtlxeService {
  // ... existing RPCs ...

  // Introspection (EF19)
  rpc ListTransformations(ListTransformationsRequest) returns (ListTransformationsResponse);
  rpc ListBundles(ListBundlesRequest) returns (ListBundlesResponse);
}
```

#### StdioEnvelope additions

```protobuf
enum MessageType {
  // ... existing values ...

  // Introspection (EF19)
  LIST_TRANSFORMATIONS_REQUEST = 9;
  LIST_TRANSFORMATIONS_RESPONSE = 19;
  LIST_BUNDLES_REQUEST = 10;
  LIST_BUNDLES_RESPONSE = 20;
}
```

#### Why this is the right approach

- **No hot-path impact** — these RPCs read from the registry, which is already an in-memory data structure. No new instrumentation needed.
- **Data already exists** — the registry already tracks transformation names, strategies, execution counts, and error counts (see `MetricsCollector.kt`). Bundle info is available from the load response. We're just exposing it.
- **Proper separation** — health stays thin ("am I alive?"), introspection answers "what do you have loaded?"
- **Wrapper recovery** — if the wrapper restarts but UTLXe stays running (e.g., pipe mode, gRPC mode), the wrapper can call `ListTransformations()` to rebuild its state instead of re-loading everything.

#### Implementation

```kotlin
// In TransportHandlers.kt
fun handleListTransformations(engine: UtlxEngine): ListTransformationsResponse {
    val transforms = engine.registry.list()
    return ListTransformationsResponse.newBuilder()
        .addAllTransformations(transforms.map { tx ->
            TransformationInfo.newBuilder()
                .setTransformationId(tx.name)
                .setStrategy(tx.strategy.name)
                .setLoadedAtMs(tx.loadedAt)
                .setExecutionCount(tx.executionCount.get())
                .setErrorCount(tx.errorCount.get())
                .setBundleId(tx.bundleId ?: "")
                .setMaxConcurrent(tx.maxConcurrent)
                .build()
        })
        .build()
}

fun handleListBundles(engine: UtlxEngine): ListBundlesResponse {
    val bundles = engine.registry.listBundles()
    return ListBundlesResponse.newBuilder()
        .addAllBundles(bundles.map { b ->
            BundleInfo.newBuilder()
                .setBundleId(b.id)
                .setVersion(b.version)
                .setChecksum(b.checksum)
                .setLoadedAtMs(b.loadedAt)
                .setTransformationCount(b.transformationIds.size)
                .addAllTransformationIds(b.transformationIds)
                .build()
        })
        .build()
}
```

---

### Part 3: Dapr-Compatible Health Probing

#### The problem

Dapr has its own health probing mechanism. When `app-protocol=grpc`, Dapr periodically calls:

```
/dapr.proto.runtime.v1.AppCallbackHealthCheck/HealthCheck
```

This is a standard Dapr proto — **not** UTLXe's custom `UtlxeService.Health()`. The request and response are both **empty messages** — it's a pure "are you alive?" check. If the app doesn't implement this service, Dapr falls back to TCP port checking (less reliable).

UTLXe currently does **not** implement `AppCallbackHealthCheck`. This means:
- Dapr doesn't know if UTLXe is truly healthy vs just has a port open
- When UTLXe is in `DRAINING` state, Dapr keeps sending messages because it thinks the app is healthy
- The EF15 heap backpressure (503 rejection) works per-request but Dapr's sidecar doesn't proactively stop routing

#### Dapr's health model (two layers)

| Layer | Endpoint | Who checks | What it means |
|-------|----------|-----------|---------------|
| **Sidecar health** | `GET /v1.0/healthz` (port 3500) | Kubernetes liveness/readiness | Dapr sidecar is initialized |
| **App health** | `AppCallbackHealthCheck/HealthCheck` (gRPC) or `GET /healthz` (HTTP) | Dapr sidecar probes the app | App is ready to receive work |

UTLXe needs to participate in layer 2.

#### Dapr Metadata API — free introspection

Dapr's sidecar exposes `GET /v1.0/metadata` which returns:

```json
{
  "id": "utlxe-wrapper",
  "runtimeVersion": "1.14.0",
  "enabledFeatures": ["ServiceInvocationStreaming"],
  "components": [
    { "name": "servicebus-input", "type": "bindings.azure.servicebustopics", "version": "v1",
      "capabilities": ["input", "output"] }
  ],
  "subscriptions": [...],
  "appConnectionProperties": {
    "port": 50051,
    "protocol": "grpc",
    "channelAddress": "127.0.0.1:50051",
    "health": {
      "healthProbeInterval": "5s",
      "healthProbeTimeout": "500ms",
      "healthThreshold": 3
    }
  },
  "extended": {
    "appPID": "12345",
    "daprRuntimeVersion": "1.14.0"
  }
}
```

Open-M's wrapper can query this endpoint to get:
- Loaded Dapr components (bindings, pub/sub, state stores) with capabilities
- App connection details (port, protocol)
- Health probe configuration
- Dapr runtime version

This is **free** — no UTLXe changes needed. The wrapper just calls `GET http://localhost:3500/v1.0/metadata`.

#### Proposed implementation

**Step 1: Implement Dapr's `AppCallbackHealthCheck` in gRPC mode**

```protobuf
// Import Dapr's callback proto (or define compatible messages)
service AppCallbackHealthCheck {
  rpc HealthCheck(HealthCheckRequest) returns (HealthCheckResponse);
}

message HealthCheckRequest {}
message HealthCheckResponse {}
```

```kotlin
// In GrpcTransport.kt — register as a second gRPC service
class DaprHealthService(private val engine: UtlxEngine) :
    AppCallbackHealthCheckGrpc.AppCallbackHealthCheckImplBase() {

    override fun healthCheck(
        request: HealthCheckRequest,
        responseObserver: StreamObserver<HealthCheckResponse>
    ) {
        if (engine.state == EngineState.RUNNING && !engine.isHeapPressure()) {
            responseObserver.onNext(HealthCheckResponse.getDefaultInstance())
            responseObserver.onCompleted()
        } else {
            // Return error status — Dapr marks app as unhealthy
            responseObserver.onError(
                Status.UNAVAILABLE
                    .withDescription("UTLXe state=${engine.state.name}, heapPressure=${engine.isHeapPressure()}")
                    .asRuntimeException()
            )
        }
    }
}
```

**Step 2: Integrate with EF15 heap backpressure**

When `AppCallbackHealthCheck` returns an error:
1. Dapr marks the app as unhealthy
2. Dapr **stops forwarding** service invocations and pub/sub messages to UTLXe
3. Messages stay in Service Bus / Event Hub (or get routed to other instances)
4. When heap pressure drops and state returns to RUNNING, the next health probe succeeds
5. Dapr resumes forwarding

This is **better than per-request 503 rejection** (EF15) because Dapr proactively stops sending rather than UTLXe having to reject each message individually.

**Step 3: Respect engine state in health probe**

| Engine State | Dapr Health Response | Effect |
|-------------|---------------------|--------|
| `STARTING` | Error (UNAVAILABLE) | Dapr waits for app to be ready |
| `RUNNING` + no heap pressure | OK (empty response) | Dapr sends messages normally |
| `RUNNING` + heap pressure | Error (UNAVAILABLE) | Dapr stops sending, messages queue |
| `DRAINING` | Error (UNAVAILABLE) | Dapr stops sending, graceful shutdown |

#### What about stdio-proto mode?

Dapr health probing only applies to gRPC mode (where Dapr communicates with UTLXe directly). In stdio-proto mode (Open-M wrapper ↔ UTLXe pipe), the wrapper manages health itself via `UtlxeService.Health()`. No change needed for stdio mode.

#### What about HTTP mode?

When `app-protocol=http`, Dapr probes `GET /healthz` on the app. UTLXe already has a `/health` HTTP endpoint (in `HealthEndpoint.kt`). Two options:
- Add a `/healthz` alias (Dapr convention) that returns 200 when healthy, 503 when not
- Or configure Dapr with `app-health-check-path=/health`

The simpler option is adding the `/healthz` alias.

---

## Summary of All Changes

| Change | Scope | Hot-path impact | Priority |
|--------|-------|----------------|----------|
| `HealthResponse.version` (field 6) | Proto + handler | None | Low |
| `HealthResponse.workers` (field 7) | Proto + handler | None | Low |
| `HealthResponse.heap_usage_percent` (field 8) | Proto + handler | None (read cached volatile) | Medium |
| `HealthResponse.heap_backpressure_threshold` (field 9) | Proto + handler | None (read config) | Medium |
| `HealthResponse.heap_pressure` (field 10) | Proto + handler | None (read cached volatile) | Medium |
| `ListTransformations` RPC | Proto + handler + StdioEnvelope | None (read-only) | Medium |
| `ListBundles` RPC | Proto + handler + StdioEnvelope | None (read-only) | Medium |
| Dapr `AppCallbackHealthCheck` (gRPC) | New gRPC service | None (probe only) | Medium |
| HTTP `/healthz` alias | HealthEndpoint.kt | None | Low |

All changes are **zero hot-path impact**. They read existing in-memory state or return static values. No per-execution instrumentation.

## Effort Estimate

| Task | Effort |
|------|--------|
| Add `version` + `workers` fields to proto + BuildInfo | 1 hour |
| `ListTransformations` proto + handler + StdioEnvelope | 0.5 day |
| `ListBundles` proto + handler + StdioEnvelope + registry tracking | 1 day |
| Dapr `AppCallbackHealthCheck` gRPC service | 0.5 day |
| HTTP `/healthz` alias | 15 minutes |
| Tests | 1 day |
| Documentation | 0.5 day |
| **Total** | **3-4 days** |

## Response to Open-M

Recommended response to UTLX-CH-001:

> UTL-X accepts 5 of 7 requested fields on `HealthResponse`:
> - `version` (field 6) — as requested
> - `workers` (field 7) — renamed from `thread_pool_size` for Azure consistency; reports resolved value
> - `heap_usage_percent` (field 8) — replaces `jvm_heap_used_bytes`/`jvm_heap_max_bytes` with a pre-computed operational health signal from EF15's backpressure system
> - `heap_backpressure_threshold` (field 9) — the configured threshold, needed to interpret heap usage
> - `heap_pressure` (field 10) — boolean: "am I actively rejecting messages right now?"
>
> The remaining 2 fields are rejected:
>
> However, UTL-X will additionally provide:
>
> 1. **`ListTransformations` and `ListBundles` RPCs** — proper introspection for `omctl inspect`, wrapper recovery, and debugging. Available via both gRPC and stdio-proto.
> 2. **Dapr `AppCallbackHealthCheck`** — UTLXe will implement the standard Dapr health probe, integrating with heap backpressure (EF15) so Dapr proactively stops routing when UTLXe is under pressure.
> 3. **Dapr Metadata API** — the wrapper should query `GET /v1.0/metadata` on the Dapr sidecar for component introspection, connection properties, and runtime version. This is free and requires no UTLXe changes.
>
> For performance metrics (avg/p99/max duration, heap usage): use OpenTelemetry (EF14) or compute aggregates wrapper-side from per-execution `ExecuteMetrics`.

## See Also

- [EF14: OpenTelemetry Tracing](EF14-opentelemetry-tracing.md) — already exports JVM metrics and execution duration
- [EF15: Heap Backpressure](EF15-heap-backpressure.md) — already tracks heap usage internally; Dapr health integration strengthens this
- [utlxe.proto](/proto/utlxe/v1/utlxe.proto) — current proto definition
- [Dapr App Health Checks](https://docs.dapr.io/operations/resiliency/health-checks/app-health/) — Dapr's health probing model
- [Dapr Metadata API](https://docs.dapr.io/reference/api/metadata_api/) — free introspection via sidecar
- [Dapr Health API Reference](https://docs.dapr.io/reference/api/health_api/) — sidecar health endpoints
- [gRPC Health Checking Protocol](https://github.com/grpc/grpc/blob/master/doc/health-checking.md) — standard gRPC health spec
- Open-M [UTLX-CH-001](/Users/magr/data/open-m/docs/changes/utlx/UTLX-CH-001-health-response-enrichment.md) — original change request

---

*Feature EF19. May 2026.*

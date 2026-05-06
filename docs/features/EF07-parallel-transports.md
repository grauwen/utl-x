# EF07: Parallel Transports

**Status:** Design  
**Priority:** Medium (required for hybrid deployments, nice-to-have for Marketplace go-live)  
**Created:** May 2026  
**Depends on:** EF03 (Admin API), EF05 (Dapr fixes)

---

## Summary

UTLXe currently starts ONE transport per `--mode` flag (http, grpc, stdio-proto, stdio-json). This feature changes the engine to run multiple transports simultaneously — HTTP + gRPC, or HTTP + stdio-proto — sharing the same `TransformationRegistry`, metrics, and health state.

## Why

| Deployment | Transports needed |
|-----------|------------------|
| Azure Marketplace (Dapr) | HTTP only (:8085 data + :8081 admin) |
| Open-M integration | stdio-proto or gRPC |
| Hybrid (Marketplace + wrapper SDK) | HTTP (:8085 + :8081) AND gRPC (:9090) |
| Future: Dapr + .NET SDK | HTTP for Dapr, gRPC for SDK — same container |

The hybrid case is the driver: a customer uses Dapr for queue-based messaging AND a .NET SDK for synchronous calls — both hitting the same engine, same transformations, same metrics.

## Current state

```kotlin
// Main.kt — picks ONE transport
val transport: TransportServer = when (mode) {
    "stdio-json" -> StdioJsonTransport()
    "stdio-proto" -> StdioProtoTransport(engine, ...)
    "grpc" -> GrpcTransport(engine, ...)
    "http" -> HttpTransport(engine, port = httpPort)
    else -> exitWithError("Unknown mode: $mode")
}
engine.start(transport)  // blocks on this single transport
```

## Proposed change

```kotlin
// Main.kt — start all configured transports
val transports = mutableListOf<TransportServer>()

// HTTP is always started (Admin API + health + Dapr/direct clients)
transports.add(HttpTransport(engine, port = httpPort))

// gRPC is optional (--grpc or --grpc-port flag)
if (grpcPort != null) {
    transports.add(GrpcTransport(engine, address = "0.0.0.0:$grpcPort"))
}

// stdio-proto is optional (--stdio flag, mutually exclusive with stdin usage)
if (stdioEnabled) {
    transports.add(StdioProtoTransport(engine, workers = workers))
}

// Start all transports — first N-1 on background threads, last one blocks
for (transport in transports.dropLast(1)) {
    Thread(transport::start, "transport-${transport.name}").apply {
        isDaemon = true
        start()
    }
}
engine.start(transports.last())  // blocks on main thread
```

### CLI changes

The `--mode` flag remains the primary transport selector. New flags enable additional transports that run alongside the primary:

```
Options (new/changed):
  --mode <mode>          Primary transport: stdio-json (default), stdio-proto, grpc, http
  --also-http            Also start HTTP transport (admin :8081 + data :8085)
  --also-grpc            Also start gRPC transport
  --also-stdio           Also start stdio-proto transport
  --http-port <port>     HTTP data plane port (default: 8085)
  --admin-port <port>    HTTP admin + health port (default: 8081)
  --grpc-port <port>     gRPC port (default: 9090)
  --grpc-socket <path>   gRPC Unix Domain Socket (alternative to TCP)
  --workers <n>          Worker thread pool size (default: CPU cores)
```

#### Usage examples

```bash
# Azure Marketplace — HTTP only (Dapr + Admin API)
utlxe --mode http

# Azure Marketplace + SDK — HTTP for Dapr, gRPC for .NET/Go SDK
utlxe --mode http --also-grpc

# Open-M — stdio-proto for wrapper, HTTP for admin/monitoring
utlxe --mode stdio-proto --also-http

# Open-M + gRPC SDK — all three transports
utlxe --mode stdio-proto --also-http --also-grpc

# gRPC only — wrapper-only deployment, no admin API
utlxe --mode grpc

# Backward compatible — all current invocations work unchanged
utlxe --mode http                           # same as today
utlxe --mode stdio-proto                    # same as today
utlxe --mode grpc --socket /tmp/utlxe.sock  # same as today
utlxe --bundle /path --mode stdio-json      # same as today
```

#### What each mode starts

| Primary mode | HTTP :8085 | HTTP :8081 (admin) | gRPC :9090 | stdio-proto |
|---|:---:|:---:|:---:|:---:|
| `--mode http` | Yes | Yes | — | — |
| `--mode http --also-grpc` | Yes | Yes | Yes | — |
| `--mode grpc` | — | — | Yes | — |
| `--mode grpc --also-http` | Yes | Yes | Yes | — |
| `--mode stdio-proto` | — | — | — | Yes |
| `--mode stdio-proto --also-http` | Yes | Yes | — | Yes |
| `--mode stdio-proto --also-http --also-grpc` | Yes | Yes | Yes | Yes |

**Note:** `--also-http` always starts BOTH ports (8085 data + 8081 admin). The admin API is inseparable from the HTTP transport — health probes, Prometheus metrics, and bundle management all run on :8081.

**Note:** `--mode http` implicitly includes the admin API. The `--also-http` flag is only needed when the primary mode is NOT http.

#### Updated help text

```
utlxe — UTL-X Production Runtime Engine v1.0.1

Usage:
  utlxe --mode http [options]                   Azure / Docker / HTTP clients
  utlxe --mode http --also-grpc [options]       HTTP + gRPC SDK (hybrid)
  utlxe --mode stdio-proto --also-http [options] Open-M + admin API
  utlxe --bundle <path> [options]               Standalone (bundle from disk)

Primary transport (--mode):
  http           HTTP REST API on :8085 (data) + :8081 (admin/health)
                 For Azure Container Apps, Docker, Dapr, direct HTTP clients.

  stdio-proto    Protobuf over stdin/stdout (Open-M / language wrapper integration)
                 Transforms loaded dynamically via LoadTransformation messages.

  grpc           gRPC server on TCP or Unix Domain Socket
                 Transforms loaded dynamically via RPCs.

  stdio-json     JSON lines over stdin/stdout (CLI, backward compat)
                 Requires --bundle. Transforms loaded from disk at startup.

Additional transports (run alongside primary):
  --also-http    Start HTTP data plane (:8085) + admin API (:8081)
  --also-grpc    Start gRPC server (:9090 or --grpc-port)
  --also-stdio   Start stdio-proto transport (pipe)

Options:
  --bundle, -b <path>    Path to bundle directory (pre-load transforms from disk)
  --config, -c <path>    Path to engine.yaml config file
  --http-port <port>     HTTP data plane port (default: 8085)
  --admin-port <port>    Admin + health port (default: 8081)
  --grpc-port <port>     gRPC port (default: 9090)
  --grpc-socket <path>   Unix Domain Socket path (gRPC, alternative to TCP)
  --workers <n>          Worker thread pool size (default: CPU cores)
  --validate             Load and compile the bundle, then exit (no processing)
  --version, -v          Print version and exit
  --help, -h             Print this help and exit
```

#### Backward compatibility

All current `--mode` invocations work unchanged. The `--also-*` flags are additive — they don't change the behavior of the primary mode. Existing Docker entrypoints, systemd units, and Kubernetes manifests continue to work without modification.

## Shared state: what's safe, what needs work

### Already thread-safe (no changes needed)

| Component | Mechanism |
|-----------|-----------|
| `TransformationRegistry` | `ConcurrentHashMap` — lock-free reads, atomic puts |
| COMPILED execution | Stateless function — input→output, no shared state |
| TEMPLATE execution | Read-only template at runtime, per-execution copy |
| COPY execution | Deep-copy skeleton per execution |
| Prometheus metrics | Atomic counters (`LongAdder`) |

### Needs implementation

| Component | Issue | Fix |
|-----------|-------|-----|
| `maxConcurrent` per transformation | Not enforced across transports | Add `Semaphore(maxConcurrent)` per transformation in registry |
| Error ring buffer (EF03) | Must be thread-safe | Use `ConcurrentLinkedDeque` with size cap |
| Dapr binding tracker (EF05) | Records OPTIONS probes from HTTP only | No issue — gRPC doesn't receive Dapr probes |
| Health endpoint | Reports ready state | Shared `AtomicBoolean` — already safe |

## Source tagging: who owns what

When multiple management paths write to the same registry, conflicts can occur. Each transformation is tagged with its source:

```kotlin
enum class TransformationSource { ADMIN_API, GRPC, STDIO_PROTO }

data class RegisteredTransformation(
    val name: String,
    val compiled: CompiledTransformation,
    val source: TransformationSource,
    val loadedAt: Instant,
    val persisted: Boolean   // true only for ADMIN_API
)
```

### Ownership rules

| Action | Allowed? | What happens |
|--------|:--------:|-------------|
| Admin API loads "X" | Always | Writes to disk + memory. `source=ADMIN_API, persisted=true` |
| gRPC loads "X" (new) | Yes | Memory only. `source=GRPC, persisted=false` |
| gRPC loads "X" (exists, source=GRPC) | Yes | Overwrites in memory. Same source. |
| gRPC loads "X" (exists, source=ADMIN_API) | **Blocked** | Error: "managed by admin-api. Delete via Admin API first." |
| Admin API loads "X" (exists, source=GRPC) | Yes | Takes ownership. Writes to disk. `source=ADMIN_API, persisted=true` |
| Admin API deletes "X" (source=ADMIN_API) | Yes | Removes from disk + memory |
| Admin API deletes "X" (source=GRPC) | Yes | Removes from memory (nothing on disk) |
| gRPC unloads "X" (source=GRPC) | Yes | Removes from memory |
| gRPC unloads "X" (source=ADMIN_API) | **Blocked** | Error: "managed by admin-api" |

### Why block gRPC overwriting Admin API?

The dangerous scenario without blocking:

```
1. Admin API uploads "orders-in" v1    → disk: v1, memory: v1
2. gRPC loads "orders-in" v2           → disk: v1, memory: v2 (silent overwrite!)
3. Container restarts                  → disk: v1, memory: v1 (silently reverted!)
```

With blocking:

```
1. Admin API uploads "orders-in" v1    → disk: v1, memory: v1
2. gRPC loads "orders-in" v2           → ERROR: "managed by admin-api"
   (explicit, no silent conflict)
```

## Port allocation

| Port | Transport | Always on? | Purpose |
|------|-----------|:---:|---------|
| 8085 | HTTP | Yes | Data plane (Dapr + direct clients) |
| 8081 | HTTP | Yes | Admin API + health + metrics |
| 9090 | gRPC | Optional (`--grpc-port`) | Language wrappers, SDKs |
| — | stdio-proto | Optional (`--stdio`) | Pipe-based wrapper (Open-M) |

Port 8085 and 8081 always start (HTTP). Port 9090 starts only when `--grpc-port 9090` is passed. stdio starts only when `--stdio` is passed.

## Response routing: no cross-contamination

Each transport manages its own connection/stream lifecycle:

```
HTTP :8085 (Javalin/Ktor):
  Thread A → TCP connection A → response returns on connection A
  Thread B → TCP connection B → response returns on connection B

gRPC :9090 (Netty):
  Thread C → gRPC stream C → response returns on stream C
  Thread D → gRPC stream D → response returns on stream D

stdio-proto (piped streams):
  Worker E → reads from stdin → writes to stdout (varint-delimited)
```

No possibility of a response landing on the wrong transport. Different servers, different protocols, different ports.

## List endpoint shows transport origin

```json
GET /admin/transformations

{
  "transformations": [
    {
      "name": "orders-in",
      "source": "admin-api",
      "persisted": true,
      "status": "ready",
      "messages_processed": 12345
    },
    {
      "name": "realtime-calc",
      "source": "grpc",
      "persisted": false,
      "status": "ready",
      "messages_processed": 890
    }
  ]
}
```

Metrics are combined across all transports — `messages_processed` counts HTTP + gRPC + stdio invocations for that transformation.

## Shutdown

Shutdown drains all transports:

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    logger.info("Shutdown signal received")
    // Stop accepting new requests on all transports
    transports.forEach { it.stop() }
    // Drain in-flight requests
    engine.stop()
})
```

## Files to modify

| File | Change |
|------|--------|
| `modules/engine/.../Main.kt` | Multi-transport startup, CLI flags `--also-grpc`, `--also-http`, `--also-stdio`, rename `--port` to `--admin-port`, remove `--port` alias |
| `modules/engine/.../registry/TransformationRegistry.kt` | Add `TransformationSource` tagging, ownership rules, `Semaphore` for `maxConcurrent` |
| `modules/engine/.../UtlxEngine.kt` | `start(transports: List<TransportServer>)` instead of single transport |
| `modules/engine/.../transport/TransportServer.kt` | Add `name` property for logging |
| `modules/engine/.../admin/AdminEndpoint.kt` (EF03) | Show `source` and `persisted` in list/get responses |

## Effort estimate

| Task | Effort |
|------|--------|
| Multi-transport startup in Main.kt | 0.5 day |
| TransformationSource tagging + ownership rules | 1 day |
| maxConcurrent Semaphore per transformation | 0.5 day |
| Thread-safe error ring buffer | 0.5 day |
| Shutdown coordination across transports | 0.5 day |
| Admin endpoint: source/persisted fields | 0.5 day |
| Tests (parallel HTTP + gRPC, conflict prevention) | 1.5 days |
| **Total** | **~5 days** |

## Relationship to other features

- **EF03** (Admin API): the `source` and `persisted` fields in list/get responses
- **EF05** (Dapr fixes): Dapr probes only happen on HTTP :8085, not on gRPC
- **EF04** (tracing): messaging triad works on all transports (proto fields + HTTP headers)
- **EF06** (pub/sub strategy): Dapr pub/sub subscriptions derived from Admin API-managed transformations only (not gRPC-loaded ephemeral ones)

---

*Feature EF07. May 2026. Design document.*
*Key insight: transports are delivery mechanisms, not ownership boundaries. The registry is the shared truth. Source tagging prevents silent conflicts between persistent (Admin API) and ephemeral (gRPC/proto) management paths.*

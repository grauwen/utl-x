# EB02: EF15 Backpressure Implementation Gaps

**Status:** Open (partially fixed)
**Severity:** Medium
**Component:** `modules/engine` — `UtlxEngine.kt`, transports
**Found:** May 2026
**Symptom:** Engine test suite timeouts, 503 rejections in tests, and missing backpressure on two transports
**Root cause:** EF15 (Heap Backpressure) was not applied consistently across all transports and the shutdown path was incomplete

---

## Overview

EF15 introduced heap backpressure to prevent OOM crashes. The implementation has three gaps:

1. **Heap monitor thread never stopped** — `stop()` doesn't interrupt the background thread
2. **HTTP transport execution endpoints unprotected** — fixed in commit `d4d95451`
3. **StdioProtoTransport and GrpcTransport have no backpressure at all** — still open

---

## Gap 1: Heap Monitor Thread Never Stopped

`UtlxEngine` spawns a daemon thread (`heap-monitor`) at construction time that polls heap usage every 100ms. The `stop()` method shuts down transports, strategies, and the health endpoint — but never interrupts the heap monitor thread.

**File:** `modules/engine/src/main/kotlin/org/apache/utlx/engine/UtlxEngine.kt`

```kotlin
// Line 50-65 — thread starts at construction, runs forever
private val heapMonitor = Thread({
    while (!Thread.currentThread().isInterrupted) {
        try {
            val rt = Runtime.getRuntime()
            val used = rt.totalMemory() - rt.freeMemory()
            val max = rt.maxMemory()
            heapUsage = used.toDouble() / max
            Thread.sleep(100)
        } catch (_: InterruptedException) {
            break
        }
    }
}, "heap-monitor").apply {
    isDaemon = true
    start()
}

// Line 276-302 — stop() does NOT interrupt heapMonitor
fun stop() {
    // ...
    allTransports.forEach { t -> t.stop() }
    registry.shutdownAll()
    healthEndpoint?.stop()
    stateRef.set(EngineState.STOPPED)
    // heapMonitor.interrupt() ← MISSING
}
```

## Impact

### In production

None visible. The process exits when the engine stops, which kills all daemon threads. The bug is masked by the JVM lifecycle.

### In tests

Each `UtlxEngine` constructed in a test leaves a zombie `heap-monitor` thread running after the test completes. In a test suite with 20+ engine tests running in the same JVM:

1. **Thread accumulation** — 20+ zombie heap-monitor threads polling `Runtime.getRuntime()` every 100ms
2. **GC pressure** — accumulated test debris (unreferenced engines, strategies, registries) increases heap usage
3. **Backpressure false positives** — zombie threads update `heapUsage` on dead engine instances, but the real problem is that elevated heap usage from accumulated garbage can push a live engine's `isHeapPressure()` to return `true`
4. **503 rejections** — subsequent tests using Dapr-style HTTP input receive 503 because the engine thinks it's under memory pressure
5. **Timeouts** — tests waiting for successful responses time out because requests are being rejected

The cascade: leaked threads → GC pressure → backpressure triggers → 503 → test timeout.

This explains why tests pass when run fresh (single JVM, clean state) but fail when run after admin-api/Dapr tests that create and discard multiple engine instances.

## Fix

Add `heapMonitor.interrupt()` to `stop()`:

```kotlin
fun stop() {
    val current = stateRef.get()
    if (current == EngineState.STOPPED) {
        return
    }

    logger.info("Stopping engine '{}'...", config.engine.name)
    stateRef.set(EngineState.DRAINING)

    // Stop heap monitor (EB02)
    heapMonitor.interrupt()

    // Stop all transports
    allTransports.forEach { t ->
        try { t.stop() } catch (e: Exception) {
            logger.warn("Error stopping transport {}: {}", t.name, e.message)
        }
    }
    if (allTransports.isEmpty()) transport?.stop()

    registry.shutdownAll()
    healthEndpoint?.stop()

    stateRef.set(EngineState.STOPPED)
    logger.info("Engine '{}' STOPPED", config.engine.name)
}
```

The thread already handles `InterruptedException` correctly (line 58-59: `catch (_: InterruptedException) { break }`), so the interrupt will cause a clean exit.

## Verification

After the fix, confirm:

```kotlin
@Test
fun `heap monitor thread stops on engine shutdown`() {
    val engine = UtlxEngine(EngineConfig.default())
    engine.initializeEmpty()

    // Verify thread is running
    val heapThread = Thread.getAllStackTraces().keys.find { it.name == "heap-monitor" }
    assertNotNull(heapThread)
    assertTrue(heapThread.isAlive)

    engine.stop()
    Thread.sleep(200) // allow interrupt to propagate

    // Verify thread has stopped
    assertFalse(heapThread.isAlive)
}
```

---

## Gap 2: HTTP Transport Execution Endpoints Unprotected (Fixed)

**Status:** Fixed (commit `d4d95451`)

The original EF15 implementation only added `isHeapPressure()` checks to the Dapr input paths (`handleDaprInput()`, `handlePubSubInput()`). The direct HTTP execution endpoints were unprotected:

| Endpoint | Backpressure before fix | After fix |
|----------|:-:|:-:|
| `POST /api/execute/{id}` | No | Yes |
| `POST /api/execute-batch/{id}` | No | Yes |
| `POST /api/execute-pipeline` | No | Yes |
| Dapr binding input | Yes | Yes |
| Dapr pub/sub input | Yes | Yes |

Under heap pressure, direct HTTP clients could still submit work, filling the heap further and eventually causing OOM.

---

## Gap 3: StdioProtoTransport and GrpcTransport Have No Backpressure

**Status:** Open

Two transports have zero backpressure checks:

### Current state

| Transport | File | Backpressure |
|-----------|------|:---:|
| HttpTransport (Dapr) | `HttpTransport.kt` | Yes |
| HttpTransport (`/api/*`) | `HttpTransport.kt` | Yes (fixed) |
| **StdioProtoTransport** | `StdioProtoTransport.kt` | **No** |
| **GrpcTransport** | `GrpcTransport.kt` | **No** |

### StdioProtoTransport (pipe mode)

Used by Open-M's wrapper. When UTLXe is under heap pressure, `ExecuteRequest` messages from the pipe are processed without any check. Under a message burst, this leads to the same OOM scenario EF15 was designed to prevent.

#### Why stdio backpressure is different from HTTP/gRPC

The pipe is **1:1** — one wrapper, one UTLXe, one pipe. This changes the dynamics:

| Transport | Topology | On rejection, caller can... |
|-----------|----------|---------------------------|
| HTTP (Dapr) | N:1 (Dapr routes to replicas) | Route to another instance, Service Bus retries later |
| gRPC | N:1 (load balancer) | Retry on another instance, automatic retry policy |
| **Stdio** | **1:1** (single pipe) | **Nothing** — no other UTLXe to route to |

When UTLXe rejects via the pipe, the wrapper is stuck. It can't route to another UTLXe. It has to buffer the message in its own memory — which moves the OOM risk from UTLXe to the wrapper.

#### But crashing is worse than rejecting

Without backpressure, a message burst OOMs UTLXe. The JVM dies (exit 137). The pipe breaks. **All** in-flight messages are lost — not just the one that caused the OOM. The wrapper has to restart UTLXe, re-load all transformations, and replay whatever it can. That's a hard failure.

With backpressure, UTLXe rejects individual messages with `TRANSIENT` errors. The pipe stays alive. All other messages continue flowing. The wrapper can:

1. Hold the rejected message and retry after a short delay (heap pressure typically clears in seconds once GC runs)
2. Signal upstream — tell Dapr/Service Bus to stop delivering via the wrapper's own backpressure
3. Log it and let Service Bus retry via its own retry policy

**Reject one message vs crash and lose everything** — rejecting is strictly better.

#### The wrapper already handles this

The wrapper receives `ExecuteResponse` with `success=false` for every error — parse failures, transformation errors, validation failures. A `TRANSIENT` error with `HEAP_PRESSURE` is just another error response. The wrapper doesn't need new logic to handle it. It already knows what to do with transient errors: don't ack the message, let the broker retry.

#### Existing flow control doesn't cover this

`StdioProtoTransport` already has implicit flow control via `CallerRunsPolicy` (line 84): when the worker pool is full, the reader thread processes the task itself, blocking stdin reads. This protects against **concurrency** overload (too many parallel messages) but not **memory** overload. A single 500MB XML message can OOM even with one worker thread. The heap check catches what flow control can't.

#### Recommended behavior for stdio

Softer than HTTP/gRPC — the wrapper should **wait and retry locally** rather than nacking to the broker immediately, because UTLXe will likely recover within seconds:

- Return `ExecuteResponse` with `success=false`, `error_class=TRANSIENT`, `error_code=HEAP_PRESSURE`, `error_phase=INTERNAL`
- The wrapper sees `TRANSIENT` + `HEAP_PRESSURE` → holds the message, retries after 1-2 seconds
- Different from `PERMANENT` errors (bad payload, missing field) where retry is pointless

### GrpcTransport (gRPC mode)

Used for direct gRPC service invocation. Same gap as HTTP was before the fix.

**Proposed fix:** In the `Execute`, `ExecuteBatch`, and `ExecutePipeline` RPC handlers, check `engine.isHeapPressure()`. If true, return gRPC status `UNAVAILABLE` with description "Heap memory pressure — retry later". gRPC clients handle `UNAVAILABLE` with automatic retry (standard gRPC retry policy).

```kotlin
// In GrpcTransport.kt — Execute RPC handler
override fun execute(request: ExecuteRequest, responseObserver: StreamObserver<ExecuteResponse>) {
    if (engine.isHeapPressure()) {
        responseObserver.onError(
            Status.UNAVAILABLE
                .withDescription("Heap memory pressure — retry later")
                .asRuntimeException()
        )
        return
    }
    // ... existing handler
}
```

### Proto change: new ErrorCode

The existing `ErrorCode` enum has no value for heap pressure. `MAX_CONCURRENT_EXCEEDED` (8) is semantically wrong (concurrency, not memory). `INTERNAL_ERROR` (10) is too generic — the wrapper can't distinguish "retry soon" from "something broke."

**Proposed addition to `utlxe.proto`:**

```protobuf
enum ErrorCode {
  ERROR_CODE_UNSPECIFIED = 0;
  TRANSFORMATION_NOT_FOUND = 1;
  BUNDLE_NOT_LOADED = 2;
  INPUT_PARSE_FAILED = 3;
  INPUT_VALIDATION_FAILED = 4;
  TRANSFORMATION_FAILED = 5;
  OUTPUT_VALIDATION_FAILED = 6;
  OUTPUT_SERIALIZATION_FAILED = 7;
  MAX_CONCURRENT_EXCEEDED = 8;
  MAX_INPUT_SIZE_EXCEEDED = 9;
  INTERNAL_ERROR = 10;
  HEAP_PRESSURE = 11;           // EF15/EB02: heap backpressure active, retry after short delay
}
```

This is backward compatible (proto3 — older clients preserve unknown enum values as integers). The wrapper uses `HEAP_PRESSURE` to distinguish from other transient errors and apply the appropriate retry strategy (wait for GC, not immediate retry).

**Used by:**
- `StdioProtoTransport`: `ExecuteResponse.error_code = HEAP_PRESSURE` + `error_class = TRANSIENT`
- `HttpTransport`: already returns JSON `"error_code": "HEAP_PRESSURE"` (commit `d4d95451`)
- `GrpcTransport`: returns gRPC `UNAVAILABLE` status (standard gRPC, no proto error code needed in the status — but if returning `ExecuteResponse` inside a success envelope, use `HEAP_PRESSURE`)

---

## Summary of All EF15 Gaps

| # | Gap | Status | Fix |
|---|-----|--------|-----|
| 1 | Heap monitor thread not stopped in `stop()` | Open | Add `heapMonitor.interrupt()` |
| 2 | HTTP `/api/execute*` missing backpressure | Fixed (`d4d95451`) | Added `isHeapPressure()` checks |
| 3a | StdioProtoTransport missing backpressure | Open | Return `ExecuteResponse` with `HEAP_PRESSURE` error code |
| 3b | GrpcTransport missing backpressure | Open | Return gRPC `UNAVAILABLE` when `isHeapPressure()` |
| 4 | Proto missing `HEAP_PRESSURE` error code | Open | Add `HEAP_PRESSURE = 11` to `ErrorCode` enum |

## Related

- [EF15: Heap Backpressure](../features/EF15-heap-backpressure.md) — introduced the heap monitor thread and backpressure mechanism
- `AdminEndpointTest.kt` — creates/destroys engines per test, triggers the thread leak (gap 1)
- `DaprIntegrationTest.kt` — stateless tests, not directly affected
- `HealthEndpointTest.kt` — uses Ktor `testApplication`, less affected but still leaks threads

---

*Engine Bug EB02. May 2026.*

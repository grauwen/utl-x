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

**Proposed fix:** Before processing `EXECUTE_REQUEST`, `EXECUTE_BATCH_REQUEST`, and `EXECUTE_PIPELINE_REQUEST` in the message handler, check `engine.isHeapPressure()`. If true, return an `ExecuteResponse` with:
- `success = false`
- `error_code = MAX_CONCURRENT_EXCEEDED` (or a new `HEAP_PRESSURE` code)
- `error_class = TRANSIENT` (wrapper should retry)
- `error = "Heap memory pressure — retry later"`

**Consideration:** The pipe is 1:1 with the wrapper. If UTLXe rejects, the wrapper has nowhere else to route. But rejecting early is still better than OOM crash — the wrapper can queue locally, signal Dapr to stop delivering, or wait and retry.

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

---

## Summary of All EF15 Gaps

| # | Gap | Status | Fix |
|---|-----|--------|-----|
| 1 | Heap monitor thread not stopped in `stop()` | Open | Add `heapMonitor.interrupt()` |
| 2 | HTTP `/api/execute*` missing backpressure | Fixed (`d4d95451`) | Added `isHeapPressure()` checks |
| 3a | StdioProtoTransport missing backpressure | Open | Return error response when `isHeapPressure()` |
| 3b | GrpcTransport missing backpressure | Open | Return `UNAVAILABLE` when `isHeapPressure()` |

## Related

- [EF15: Heap Backpressure](../features/EF15-heap-backpressure.md) — introduced the heap monitor thread and backpressure mechanism
- `AdminEndpointTest.kt` — creates/destroys engines per test, triggers the thread leak (gap 1)
- `DaprIntegrationTest.kt` — stateless tests, not directly affected
- `HealthEndpointTest.kt` — uses Ktor `testApplication`, less affected but still leaks threads

---

*Engine Bug EB02. May 2026.*
